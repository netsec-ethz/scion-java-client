// Copyright 2023 ETH Zurich
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.scion.internal;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import org.scion.ScionRuntimeException;
import org.scion.ScionUtil;
import org.scion.proto.control_plane.Seg;
import org.scion.proto.control_plane.SegmentLookupServiceGrpc;
import org.scion.proto.crypto.Signed;
import org.scion.proto.daemon.Daemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class gets segment information from a path service and constructs paths.
 *
 * <p>Assumption: When requesting CORE segments (inter-ISD or intra-ISD), the path service will
 * return at least one segment for every AS in the src-ISD to connecting to every AS in the dst-ISD.
 * Intra-ISD this is ensured by mutual registration, inter-ISD this is ensured by sending PCB to all
 * other core-AS.<br>
 * Source: <a href="https://datatracker.ietf.org/doc/draft-dekater-scion-controlplane/">...</a>
 *
 * <p>3.2. Core Path-Segment Registration <br>
 * The core beaconing process creates path segments from core AS to core AS. These core-segments are
 * then added to the control service path database of the core AS that created the segment, so that
 * local and remote endpoints can obtain and use these core-segments. In contrast to the intra-ISD
 * registration procedure, there is no need to register core-segments with other core ASes (as each
 * core AS will receive PCBs originated from every other core AS).
 *
 * <p>
 */
public class Segments {
  private static final Logger LOG = LoggerFactory.getLogger(Segments.class.getName());

  private Segments() {}

  private static List<Daemon.Path> combineSegments(
      List<Seg.SegmentsResponse> segments,
      long srcIsdAs,
      long dstIsdAs,
      ScionBootstrapper brLookup) {
    if (segments.size() == 1) {
      return combineSegment(segments.get(0), brLookup);
    } else if (segments.size() == 2) {
      return combineTwoSegments(segments.get(0), segments.get(1), srcIsdAs, dstIsdAs, brLookup);
    }
    return combineThreeSegments(
        segments.get(0), segments.get(1), segments.get(2), srcIsdAs, dstIsdAs, brLookup);
  }

  private static List<Daemon.Path> combineSegment(
      Seg.SegmentsResponse segments, ScionBootstrapper brLookup) {
    List<Daemon.Path> paths = new ArrayList<>();
    for (Seg.PathSegment pathSegment : get(segments)) {
      paths.add(buildPath(brLookup, pathSegment));
    }
    return paths;
  }

  /**
   * Creates path from two segments. E.g. Up+Core or Core+Down.
   *
   * @param segments0 Up or Core segments
   * @param segments1 Core or Down segments
   * @param srcIsdAs src ISD/AS
   * @param dstIsdAs src ISD/AS
   * @param brLookup border router lookup resource
   * @return Paths
   */
  private static List<Daemon.Path> combineTwoSegments(
      Seg.SegmentsResponse segments0,
      Seg.SegmentsResponse segments1,
      long srcIsdAs,
      long dstIsdAs,
      ScionBootstrapper brLookup) {
    // Map IsdAs to pathSegment
    MultiMap<Long, Seg.PathSegment> segmentsMap1 = createSegmentsMap(segments1, dstIsdAs);

    List<Daemon.Path> paths = new ArrayList<>();
    for (Seg.PathSegment pathSegment0 : get(segments0)) {
      long middleIsdAs = getOtherIsdAs(srcIsdAs, pathSegment0);
      ArrayList<Seg.PathSegment> segmentList1 = segmentsMap1.get(middleIsdAs);
      if (segmentList1 != null) {
        for (Seg.PathSegment pathSegment1 : segmentList1) {
          paths.add(buildPath(brLookup, pathSegment0, pathSegment1));
        }
      }
    }
    return paths;
  }

  private static List<Daemon.Path> combineThreeSegments(
      Seg.SegmentsResponse segmentsUp,
      Seg.SegmentsResponse segmentsCore,
      Seg.SegmentsResponse segmentsDown,
      long srcIsdAs,
      long dstIsdAs,
      ScionBootstrapper brLookup) {
    // Map IsdAs to pathSegment
    MultiMap<Long, Seg.PathSegment> upSegments = createSegmentsMap(segmentsUp, srcIsdAs);
    MultiMap<Long, Seg.PathSegment> downSegments = createSegmentsMap(segmentsDown, dstIsdAs);

    List<Daemon.Path> paths = new ArrayList<>();
    for (Seg.PathSegment pathSeg : get(segmentsCore)) {
      long[] endIAs = getEndingIAs(pathSeg);
      if (upSegments.get(endIAs[0]) != null && downSegments.get(endIAs[1]) != null) {
        buildPath(paths, upSegments.get(endIAs[0]), pathSeg, downSegments.get(endIAs[1]), brLookup);
      } else if (upSegments.get(endIAs[1]) != null && downSegments.get(endIAs[0]) != null) {
        buildPath(paths, upSegments.get(endIAs[1]), pathSeg, downSegments.get(endIAs[0]), brLookup);
      }
    }
    return paths;
  }

  private static void buildPath(
      List<Daemon.Path> paths,
      List<Seg.PathSegment> segmentsUp,
      Seg.PathSegment segCore,
      List<Seg.PathSegment> segmentsDown,
      ScionBootstrapper brLookup) {
    for (Seg.PathSegment segUp : segmentsUp) {
      for (Seg.PathSegment segDown : segmentsDown) {
        paths.add(buildPath(brLookup, segUp, segCore, segDown));
      }
    }
  }

  private static Daemon.Path buildPath(ScionBootstrapper brLookup, Seg.PathSegment... segments) {
    Daemon.Path.Builder path = Daemon.Path.newBuilder();
    ByteBuffer raw = ByteBuffer.allocate(1000);

    Seg.SegmentInformation[] infos = new Seg.SegmentInformation[segments.length];
    for (int i = 0; i < segments.length; i++) {
      infos[i] = getInfo(segments[i]);
    }

    // path meta header
    int patrhMetaHeader = 0;
    for (int i = 0; i < segments.length; i++) {
      int hopCount = segments[i].getAsEntriesCount();
      patrhMetaHeader |= hopCount << (6 * (2 - i));
    }
    raw.putInt(patrhMetaHeader);

    // info fields
    boolean[] reversed = new boolean[segments.length];
    long startIA = brLookup.getLocalIsdAs();
    final ByteUtil.MutLong endingIA = new ByteUtil.MutLong(-1);
    for (int i = 0; i < infos.length; i++) {
      reversed[i] = isReversed(segments[i], startIA, endingIA);
      writeInfoField(raw, infos[i], reversed[i]);
      startIA = endingIA.get();
    }

    // hop fields
    path.setMtu(brLookup.getLocalMtu());
    for (int i = 0; i < segments.length; i++) {
      // bytePosSegID: 6 = 4 bytes path head + 2 byte flag in first info field
      writeHopFields(path, raw, 6 + i * 8, segments[i], reversed[i], infos[i]);
    }

    raw.flip();
    path.setRaw(ByteString.copyFrom(raw));

    // TODO where do we get these?
    //    segUp.getSegmentInfo();
    //    path.setLatency();
    //    path.setInternalHops();
    //    path.setNotes();
    // First hop
    String firstHop = brLookup.getBorderRouterAddress((int) path.getInterfaces(0).getId());
    Daemon.Underlay underlay = Daemon.Underlay.newBuilder().setAddress(firstHop).build();
    Daemon.Interface interfaceAddr = Daemon.Interface.newBuilder().setAddress(underlay).build();
    path.setInterface(interfaceAddr);

    return path.build();
  }

  private static boolean isReversed(
      Seg.PathSegment pathSegment, long startIA, ByteUtil.MutLong endIA) {
    Seg.ASEntrySignedBody body0 = getBody(pathSegment.getAsEntriesList().get(0));
    Seg.ASEntry asEntryN = pathSegment.getAsEntriesList().get(pathSegment.getAsEntriesCount() - 1);
    Seg.ASEntrySignedBody bodyN = getBody(asEntryN);
    if (body0.getIsdAs() == startIA) {
      endIA.set(bodyN.getIsdAs());
      return false;
    } else if (bodyN.getIsdAs() == startIA) {
      endIA.set(body0.getIsdAs());
      return true;
    }
    // TODO support short-cut and on-path IAs
    throw new UnsupportedOperationException("Relevant IA is not an ending IA!");
  }

  private static long calcExpTime(long baseTime, int deltaTime) {
    return baseTime + (long) (1 + deltaTime) * 24 * 60 * 60 / 256;
  }

  private static void writeInfoField(
      ByteBuffer raw, Seg.SegmentInformation info, boolean reversed) {
    int inf0 = ((reversed ? 0 : 1) << 24) | info.getSegmentId();
    raw.putInt(inf0);
    raw.putInt(ByteUtil.toInt(info.getTimestamp()));
  }

  private static void writeHopFields(
      Daemon.Path.Builder path,
      ByteBuffer raw,
      int bytePosSegID,
      Seg.PathSegment pathSegment,
      boolean reversed,
      Seg.SegmentInformation info) {
    final int n = pathSegment.getAsEntriesCount();
    int minExpiry = Integer.MAX_VALUE;
    for (int i = 0; i < n; i++) {
      int pos = reversed ? (n - i - 1) : i;
      Seg.ASEntrySignedBody body = getBody(pathSegment.getAsEntriesList().get(pos));
      Seg.HopField hopField = body.getHopEntry().getHopField();

      raw.put((byte) 0);
      raw.put(ByteUtil.toByte(hopField.getExpTime()));
      raw.putShort(ByteUtil.toShort(hopField.getIngress()));
      raw.putShort(ByteUtil.toShort(hopField.getEgress()));
      ByteString mac = hopField.getMac();
      for (int j = 0; j < 6; j++) {
        raw.put(mac.byteAt(j));
      }
      if (reversed && i > 0) {
        raw.put(bytePosSegID, ByteUtil.toByte(raw.get(bytePosSegID) ^ mac.byteAt(0)));
        raw.put(bytePosSegID + 1, ByteUtil.toByte(raw.get(bytePosSegID + 1) ^ mac.byteAt(1)));
      }
      minExpiry = Math.min(minExpiry, hopField.getExpTime());
      path.setMtu(Math.min(path.getMtu(), body.getMtu()));

      boolean addInterfaces = (reversed && pos > 0) || (!reversed && pos < n - 1);
      if (addInterfaces) {
        Daemon.PathInterface.Builder pib = Daemon.PathInterface.newBuilder();
        pib.setId(reversed ? hopField.getIngress() : hopField.getEgress());
        path.addInterfaces(pib.setIsdAs(body.getIsdAs()).build());

        Daemon.PathInterface.Builder pib2 = Daemon.PathInterface.newBuilder();
        int pos2 = reversed ? pos - 1 : pos + 1;
        Seg.ASEntrySignedBody body2 = getBody(pathSegment.getAsEntriesList().get(pos2));
        Seg.HopField hopField2 = body2.getHopEntry().getHopField();
        pib2.setId(reversed ? hopField2.getEgress() : hopField2.getIngress());
        path.addInterfaces(pib2.setIsdAs(body2.getIsdAs()).build());
      }
    }

    // expiration
    long time = calcExpTime(info.getTimestamp(), minExpiry);
    if (time < path.getExpiration().getSeconds()) {
      path.setExpiration(Timestamp.newBuilder().setSeconds(minExpiry).build());
    }
  }

  private static MultiMap<Long, Seg.PathSegment> createSegmentsMap(
      Seg.SegmentsResponse response, long knownIsdAs) {
    MultiMap<Long, Seg.PathSegment> map = new MultiMap<>();
    for (Map.Entry<Integer, Seg.SegmentsResponse.Segments> segmentsEntry :
        response.getSegmentsMap().entrySet()) {
      for (Seg.PathSegment pathSeg : segmentsEntry.getValue().getSegmentsList()) {
        long unknownIsdAs = getOtherIsdAs(knownIsdAs, pathSeg);
        if (unknownIsdAs != -1) {
          map.put(unknownIsdAs, pathSeg);
        }
      }
    }
    return map;
  }

  private static long getOtherIsdAs(long isdAs, Seg.PathSegment seg) {
    long[] endings = getEndingIAs(seg);
    if (endings[0] == isdAs) {
      return endings[1];
    } else if (endings[1] == isdAs) {
      return endings[0];
    }
    return -1;
  }

  /**
   * @param seg path segment
   * @return first and last ISD/AS of the path segment
   */
  static long[] getEndingIAs(Seg.PathSegment seg) {
    Seg.ASEntry asEntryFirst = seg.getAsEntries(0);
    Seg.ASEntry asEntryLast = seg.getAsEntries(seg.getAsEntriesCount() - 1);
    if (!asEntryFirst.hasSigned() || !asEntryLast.hasSigned()) {
      throw new UnsupportedOperationException("Unsigned entries not (yet) supported"); // TODO
    }
    Seg.ASEntrySignedBody bodyFirst = getBody(asEntryFirst.getSigned());
    Seg.ASEntrySignedBody bodyLast = getBody(asEntryLast.getSigned());
    return new long[] {bodyFirst.getIsdAs(), bodyLast.getIsdAs()};
  }

  private static Seg.ASEntrySignedBody getBody(Signed.SignedMessage sm) {
    try {
      Signed.HeaderAndBodyInternal habi =
          Signed.HeaderAndBodyInternal.parseFrom(sm.getHeaderAndBody());
      return Seg.ASEntrySignedBody.parseFrom(habi.getBody());
    } catch (InvalidProtocolBufferException e) {
      throw new ScionRuntimeException(e);
    }
  }

  private static Seg.ASEntrySignedBody getBody(Seg.ASEntry asEntry) {
    // Let's assumed they are all signed
    Signed.SignedMessage sm = asEntry.getSigned();
    return getBody(sm);
  }

  private static Seg.SegmentInformation getInfo(Seg.PathSegment pathSegment) {
    try {
      return Seg.SegmentInformation.parseFrom(pathSegment.getSegmentInfo());
    } catch (InvalidProtocolBufferException e) {
      throw new ScionRuntimeException(e);
    }
  }

  private static boolean[] containsIsdAs(
      Seg.SegmentsResponse segments, long srcIsdAs, long dstIsdAs) {
    boolean[] found = new boolean[] {false, false};
    for (Seg.PathSegment seg : get(segments)) {
      Seg.ASEntry asEntryFirst = seg.getAsEntries(0);
      Seg.ASEntry asEntryLast = seg.getAsEntries(seg.getAsEntriesCount() - 1);
      if (!asEntryFirst.hasSigned() || !asEntryLast.hasSigned()) {
        throw new UnsupportedOperationException("Unsigned entries are not supported");
      }
      // TODO for shortcut/on-path add ALL instead of just ends
      long iaFirst = getBody(asEntryFirst.getSigned()).getIsdAs();
      long iaLast = getBody(asEntryLast.getSigned()).getIsdAs();
      found[0] |= (iaFirst == srcIsdAs) || (iaLast == srcIsdAs);
      found[1] |= (iaFirst == dstIsdAs) || (iaLast == dstIsdAs);
    }
    return found;
  }

  private static Seg.SegmentsResponse getSegments(
      SegmentLookupServiceGrpc.SegmentLookupServiceBlockingStub segmentStub,
      long srcIsdAs,
      long dstIsdAs) {
    if (LOG.isInfoEnabled()) {
      LOG.info(
          "Requesting segments: {} {}",
          ScionUtil.toStringIA(srcIsdAs),
          ScionUtil.toStringIA(dstIsdAs));
    }
    if (srcIsdAs == dstIsdAs && !isWildcard(srcIsdAs)) {
      return null;
    }
    Seg.SegmentsRequest request =
        Seg.SegmentsRequest.newBuilder().setSrcIsdAs(srcIsdAs).setDstIsdAs(dstIsdAs).build();
    try {
      Seg.SegmentsResponse response = segmentStub.segments(request);
      if (response.getSegmentsMap().size() > 1) {
        // TODO fix! We need to be able to handle more than one segment collection (?)
        throw new UnsupportedOperationException();
      }
      return response;
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode().equals(Status.Code.UNKNOWN)
          && e.getMessage().contains("TRC not found")) {
        String msg = ScionUtil.toStringIA(srcIsdAs) + " / " + ScionUtil.toStringIA(dstIsdAs);
        throw new ScionRuntimeException(
            "Error while getting Segments: unknown src/dst ISD-AS: " + msg, e);
      }
      if (e.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
        throw new ScionRuntimeException(
            "Error while getting Segments: cannot connect to SCION network", e);
      }
      throw new ScionRuntimeException("Error while getting Segment info: " + e.getMessage(), e);
    }
  }

  private static List<Seg.PathSegment> get(Seg.SegmentsResponse response) { // TODO remove
    List<Seg.PathSegment> pathSegments = new ArrayList<>();
    for (Map.Entry<Integer, Seg.SegmentsResponse.Segments> seg :
        response.getSegmentsMap().entrySet()) {
      pathSegments.addAll(seg.getValue().getSegmentsList());
    }

    return pathSegments;
  }

  public static List<Daemon.Path> getPaths(
      SegmentLookupServiceGrpc.SegmentLookupServiceBlockingStub segmentStub,
      ScionBootstrapper brLookup,
      long srcIsdAs,
      long dstIsdAs) {
    // Cases:
    // A: src==dst
    // B: srcISD==dstISD; dst==core
    // C: srcISD==dstISD; src==core
    // D: srcISD==dstISD; src==core, dst==core
    // E: srcISD==dstISD;
    // F: srcISD!=dstISD; dst==core
    // G: srcISD!=dstISD; src==core
    // H: srcISD!=dstISD;
    long srcWildcard = toWildcard(srcIsdAs);
    long dstWildcard = toWildcard(dstIsdAs);
    int srcISD = ScionUtil.extractIsd(srcIsdAs);
    int dstISD = ScionUtil.extractIsd(dstIsdAs);

    if (srcIsdAs == dstIsdAs) {
      // case A: same AS, return empty path
      Daemon.Path.Builder path = Daemon.Path.newBuilder();
      path.setMtu(brLookup.getLocalMtu());
      path.setExpiration(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build());
      return Collections.singletonList(path.build());
    }

    // TODO in future we can find out whether an AS is CORE by parsing the TRC files:
    //      https://docs.anapaya.net/en/latest/resources/isd-as-assignments/#as-assignments
    //     ./bazel-bin/scion-pki/cmd/scion-pki/scion-pki_/scion-pki trc inspect
    //         ../../Downloads/ISD64.bundle
    //     Or we use logic. If SRC is not CORE then DST must be CORE.

    long from = srcIsdAs;
    long to = dstIsdAs;
    List<Seg.SegmentsResponse> segments = new ArrayList<>();
    if (!brLookup.isLocalAsCore()) {
      // get UP segments
      // TODO find out if dstIsAs is core and directly ask for it.
      Seg.SegmentsResponse segmentsUp = getSegments(segmentStub, srcIsdAs, srcWildcard);
      boolean[] containsIsdAs = containsIsdAs(segmentsUp, srcIsdAs, dstIsdAs);
      if (containsIsdAs[1]) {
        // case B: DST is core
        return combineSegment(segmentsUp, brLookup);
      }
      segments.add(segmentsUp);
      from = srcWildcard;
    }

    if (srcISD == dstISD) {
      // cases C, D, E
      // TODO this is an expensive way to find out whether DST is CORE
      Seg.SegmentsResponse segmentsCoreOrDown = getSegments(segmentStub, from, dstIsdAs);
      // TODO this 'if' is horrible. Also, the !=null should NOT be done in getSegments()
      if (segmentsCoreOrDown != null && !get(segmentsCoreOrDown).isEmpty()) {
        // Okay, we found a direct route from src(Wildcard) to DST
        segments.add(segmentsCoreOrDown);
        List<Daemon.Path> paths = combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
        if (!paths.isEmpty()) {
          return paths;
        }
        // okay, we need CORE!
        segments.remove(segments.size() - 1);
        Seg.SegmentsResponse segmentsCore = getSegments(segmentStub, from, dstWildcard);
        segments.add(segmentsCore);
        segments.add(segmentsCoreOrDown);
        return combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
      }
      // Try again with wildcard (DST is neither core nor a child of FROM)
      Seg.SegmentsResponse segmentsCore = getSegments(segmentStub, from, dstWildcard);
      boolean[] coreHasIA = containsIsdAs(segmentsCore, from, dstIsdAs);
      segments.add(segmentsCore);
      if (coreHasIA[1]) {
        // case D: DST is core
        return combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
      } else {
        from = dstWildcard;
        // case C: DST is not core
        // We have to query down segments because SRC may not have a segment connected to DST
        Seg.SegmentsResponse segmentsDown = getSegments(segmentStub, from, dstIsdAs);
        segments.add(segmentsDown);
        return Segments.combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
      }
    }
    // remaining cases: F, G, H
    Seg.SegmentsResponse segmentsCore = getSegments(segmentStub, from, dstWildcard);
    boolean[] localCores = Segments.containsIsdAs(segmentsCore, srcIsdAs, dstIsdAs);
    segments.add(segmentsCore);
    if (localCores[1]) {
      return Segments.combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
    }

    Seg.SegmentsResponse segmentsDown = getSegments(segmentStub, dstWildcard, dstIsdAs);
    segments.add(segmentsDown);
    return Segments.combineSegments(segments, srcIsdAs, dstIsdAs, brLookup);
  }

  private static long toWildcard(long isdAs) {
    return (isdAs >>> 48) << 48;
  }

  private static boolean isWildcard(long isdAs) {
    return toWildcard(isdAs) == isdAs;
  }
}
