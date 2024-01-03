// Copyright 2024 ETH Zurich
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
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.*;
import org.scion.PackageVisibilityHelper;
import org.scion.Scion;
import org.scion.ScionUtil;
import org.scion.proto.control_plane.Seg;
import org.scion.proto.crypto.Signed;
import org.scion.proto.daemon.Daemon;
import org.scion.testutil.DNSUtil;
import org.scion.testutil.MockControlServer;
import org.scion.testutil.MockTopologyServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cases: <br>
 * A (-): src == dst <br>
 * B (UP): srcISD == dstISD; dst == core <br>
 * C (DOWN): srcISD == dstISD; src == core <br>
 * D (CORE): srcISD == dstISD; src == core, dst == core <br>
 * E (-): srcISD == dstISD; <br>
 * F (UP, CORE): srcISD != dstISD; dst == core <br>
 * G (CORE, DOWN): srcISD != dstISD; src == core <br>
 * H (UP, CORE, DOWN): srcISD != dstISD;
 */
public class SegmentsTest {
  private static final String AS_HOST = "my-as-host.org";

  private static MockTopologyServer topoServer;
  private static MockControlServer controlServer;

  @BeforeAll
  public static void beforeAll() throws IOException {

    // TODO FIX misunderstanding with core AS
    //   - 1<<48 give the ISD, but NOT the coreAS
    //   - a core AS always has AS-nubmer != 0 !!!!!
    //   Fix this is Segments.java

    topoServer = MockTopologyServer.start(Paths.get("topology-dummy.json"));
    InetSocketAddress topoAddr = topoServer.getAddress();
    DNSUtil.installNAPTR(AS_HOST, topoAddr.getAddress().getAddress(), topoAddr.getPort());
    controlServer = MockControlServer.start(31006); // TODO get port from topo
    // controlServer.addSegments(1, 2, Seg.SegmentsResponse.newBuilder().build());
  }

  @AfterEach
  public void afterEach() {
    controlServer.clearSegments();
    topoServer.getAndResetCallCount();
    controlServer.getAndResetCallCount();
  }

  @AfterAll
  public static void afterAll() {
    controlServer.close();
    topoServer.close();
    DNSUtil.clear();
  }

  @Disabled
  @Test
  public void caseB_Up_Production() throws IOException {
    //    Requesting segments: 64-2:0:9 -> 64-0:0:0
    //    SEG: key=1 -> n=2
    //    PathSeg: size=10
    //    SegInfo:  ts=2024-01-03T12:13:49Z  id=49168
    //      AS: signed=96   signature size=70
    //       AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T12:13:49.787930565Z
    //       AS Body: IA=64-0:0:22f nextIA=64-2:0:9  mtu=9000
    //         HopEntry: true mtu=0
    //           HopField: exp=63 ingress=0 egress=5
    //     AS: signed=91   signature size=71
    //       AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T12:14:12.763701859Z
    //       AS Body: IA=64-2:0:9 nextIA=0-0:0:0  mtu=8972
    //         HopEntry: true mtu=9000
    //           HopField: exp=63 ingress=1 egress=0
    //    PathSeg: size=10
    //    SegInfo:  ts=2024-01-03T12:13:50Z  id=30722
    //     AS: signed=95   signature size=70
    //       AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T12:13:50.038748424Z
    //       AS Body: IA=64-0:0:22f nextIA=64-2:0:9  mtu=9000
    //         HopEntry: true mtu=0
    //           HopField: exp=63 ingress=0 egress=6
    //     AS: signed=91   signature size=72
    //       AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T12:14:10.451665722Z
    //       AS Body: IA=64-2:0:9 nextIA=0-0:0:0  mtu=8972
    //         HopEntry: true mtu=1472
    //           HopField: exp=63 ingress=2 egress=0
    long srcIA = ScionUtil.parseIA("64-2:0:9");
    long dstIA = ScionUtil.parseIA("64-0:0:0"); // TODO this is a wildcard address...
    Seg.HopEntry he00 = buildHopEntry(0, buildHopField(63, 0, 5));
    Seg.ASEntry ase00 = buildASEntry("64-0:0:22f", "64-2:0:9", 9000, he00);
    Seg.HopEntry he01 = buildHopEntry(9000, buildHopField(63, 1, 0));
    Seg.ASEntry ase01 = buildASEntry("64-2:0:9", "0-0:0:0", 8972, he01);
    Seg.PathSegment path0 = buildPath(49168, ase00, ase01);

    Seg.HopEntry he10 = buildHopEntry(0, buildHopField(63, 0, 6));
    Seg.ASEntry ase10 = buildASEntry("64-0:0:22f", "64-2:0:9", 9000, he10);
    Seg.HopEntry he11 = buildHopEntry(1472, buildHopField(63, 2, 0));
    Seg.ASEntry ase11 = buildASEntry("64-2:0:9", "0-0:0:0", 8972, he11);
    Seg.PathSegment path1 = buildPath(30722, ase10, ase11);

    controlServer.addResponse(
        srcIA, false, dstIA, true, buildResponse(Seg.SegmentType.SEGMENT_TYPE_UP, path0, path1));

    try (Scion.CloseableService ss = Scion.newServiceWithDNS(AS_HOST)) {
      List<Daemon.Path> paths = PackageVisibilityHelper.getPathListCS(ss, srcIA, dstIA);
      assertNotNull(paths);
      assertFalse(paths.isEmpty());
    }
    assertEquals(1, topoServer.getAndResetCallCount());
    assertEquals(1, controlServer.getAndResetCallCount());
  }

  @Test
  void caseB_Up() throws IOException {
    //  Requesting segments: 1-ff00:0:111 -> 1-ff00:0:110
    //  SEG: key=SEGMENT_TYPE_UP -> n=1
    //  PathSeg: size=10
    //  SegInfo:  ts=2024-01-03T14:51:44Z  id=31466
    //    AS: signed=92   signature size=72
    //    AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T14:51:44.085068203Z
    //    AS Body: IA=1-ff00:0:110 nextIA=1-ff00:0:111  mtu=1400
    //      HopEntry: true mtu=0
    //        HopField: exp=63 ingress=0 egress=1
    //    AS: signed=89   signature size=70
    //    AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T14:51:44.587225332Z
    //    AS Body: IA=1-ff00:0:111 nextIA=0-0:0:0  mtu=1472
    //      HopEntry: true mtu=1280
    //        HopField: exp=63 ingress=41 egress=0

    long srcIA = ScionUtil.parseIA("1-ff00:0:111");
    long dstIA = ScionUtil.parseIA("1-ff00:0:110");
    Seg.HopEntry he00 = buildHopEntry(0, buildHopField(63, 0, 1));
    Seg.ASEntry ase00 = buildASEntry("1-ff00:0:110", "1-ff00:0:111", 1400, he00);
    Seg.HopEntry he01 = buildHopEntry(1280, buildHopField(63, 41, 0));
    Seg.ASEntry ase01 = buildASEntry("1-ff00:0:111", "0-0:0:0", 1472, he01);
    Seg.PathSegment path0 = buildPath(31466, ase00, ase01);

    controlServer.addResponse(
        srcIA, false, dstIA, true, buildResponse(Seg.SegmentType.SEGMENT_TYPE_UP, path0));

    try (Scion.CloseableService ss = Scion.newServiceWithDNS(AS_HOST)) {
      List<Daemon.Path> paths = PackageVisibilityHelper.getPathListCS(ss, srcIA, dstIA);
      assertNotNull(paths);
      assertFalse(paths.isEmpty());
    }
    assertEquals(1, topoServer.getAndResetCallCount());
    assertEquals(1, controlServer.getAndResetCallCount());
  }

  @Test
  void caseC_Down() throws IOException {
    //  Requesting segments: 1-ff00:0:110 -> 1-ff00:0:111
    //  SEG: key=SEGMENT_TYPE_DOWN -> n=1
    //  PathSeg: size=10
    //  SegInfo:  ts=2024-01-03T14:27:54Z  id=17889
    //    AS: signed=92   signature size=71
    //    AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T14:27:54.197517906Z
    //    AS Body: IA=1-ff00:0:110 nextIA=1-ff00:0:111  mtu=1400
    //      HopEntry: true mtu=0
    //        HopField: exp=63 ingress=0 egress=1
    //    AS: signed=89   signature size=71
    //    AS header: SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256  time=2024-01-03T14:27:54.696855774Z
    //    AS Body: IA=1-ff00:0:111 nextIA=0-0:0:0  mtu=1472
    //      HopEntry: true mtu=1280
    //        HopField: exp=63 ingress=41 egress=0

    long srcIA = ScionUtil.parseIA("1-ff00:0:110");
    long dstIA = ScionUtil.parseIA("1-ff00:0:111");
    Seg.HopEntry he00 = buildHopEntry(0, buildHopField(63, 0, 1));
    Seg.ASEntry ase00 = buildASEntry("1-ff00:0:110", "1-ff00:0:111", 1400, he00);
    Seg.HopEntry he01 = buildHopEntry(1280, buildHopField(63, 41, 0));
    Seg.ASEntry ase01 = buildASEntry("1-ff00:0:111", "0-0:0:0", 1472, he01);
    Seg.PathSegment path0 = buildPath(17889, ase00, ase01);

    controlServer.addResponse(
        srcIA, true, dstIA, false, buildResponse(Seg.SegmentType.SEGMENT_TYPE_UP, path0));

    try (Scion.CloseableService ss = Scion.newServiceWithDNS(AS_HOST)) {
      List<Daemon.Path> paths = PackageVisibilityHelper.getPathListCS(ss, srcIA, dstIA);
      assertNotNull(paths);
      assertFalse(paths.isEmpty());
    }
    assertEquals(1, topoServer.getAndResetCallCount());
    assertEquals(2, controlServer.getAndResetCallCount());
  }

  @Test
  void caseD_SameCoreAS() throws IOException {
    long srcIA = ScionUtil.parseIA("1-ff00:0:110");
    long dstIA = ScionUtil.parseIA("1-ff00:0:110");
    try (Scion.CloseableService ss = Scion.newServiceWithDNS(AS_HOST)) {
      List<Daemon.Path> paths = PackageVisibilityHelper.getPathListCS(ss, srcIA, dstIA);
      assertNotNull(paths);
      assertTrue(paths.isEmpty());
    }
    assertEquals(1, topoServer.getAndResetCallCount());
    assertEquals(0, controlServer.getAndResetCallCount());
  }

  @Test
  void caseE_SameNonCoreAS() throws IOException {
    long srcIA = ScionUtil.parseIA("1-ff00:0:111");
    long dstIA = ScionUtil.parseIA("1-ff00:0:111");
    try (Scion.CloseableService ss = Scion.newServiceWithDNS(AS_HOST)) {
      List<Daemon.Path> paths = PackageVisibilityHelper.getPathListCS(ss, srcIA, dstIA);
      assertNotNull(paths);
      assertTrue(paths.isEmpty());
    }
    assertEquals(1, topoServer.getAndResetCallCount());
    assertEquals(0, controlServer.getAndResetCallCount());
  }

  private static Seg.HopField buildHopField(int expiry, int ingress, int egress) {
    ByteString mac = ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6});
    return Seg.HopField.newBuilder()
        .setExpTime(expiry)
        .setIngress(ingress)
        .setEgress(egress)
        .setMac(mac)
        .build();
  }

  private static Seg.HopEntry buildHopEntry(int mtu, Seg.HopField hf) {
    return Seg.HopEntry.newBuilder().setIngressMtu(mtu).setHopField(hf).build();
  }

  private static Seg.ASEntry buildASEntry(String isdAs, String nextIA, int mtu, Seg.HopEntry he) {
    return buildASEntry(ScionUtil.parseIA(isdAs), ScionUtil.parseIA(nextIA), mtu, he);
  }

  private static Seg.ASEntry buildASEntry(long isdAs, long nextIA, int mtu, Seg.HopEntry he) {
    Instant now = Instant.now();
    Signed.Header header =
        Signed.Header.newBuilder()
            .setSignatureAlgorithm(Signed.SignatureAlgorithm.SIGNATURE_ALGORITHM_ECDSA_WITH_SHA256)
            .setTimestamp(now())
            .build();
    Seg.ASEntrySignedBody body =
        Seg.ASEntrySignedBody.newBuilder()
            .setIsdAs(isdAs)
            .setNextIsdAs(nextIA)
            .setMtu(mtu)
            .setHopEntry(he)
            .build();
    Signed.HeaderAndBodyInternal habi =
        Signed.HeaderAndBodyInternal.newBuilder()
            .setHeader(header.toByteString())
            .setBody(body.toByteString())
            .build();
    Signed.SignedMessage sm =
        Signed.SignedMessage.newBuilder().setHeaderAndBody(habi.toByteString()).build();
    return Seg.ASEntry.newBuilder().setSigned(sm).build();
  }

  private static Seg.PathSegment buildPath(int id, Seg.ASEntry... entries) {
    long now = Instant.now().getEpochSecond();
    Seg.SegmentInformation info =
        Seg.SegmentInformation.newBuilder().setSegmentId(id).setTimestamp(now).build();
    Seg.PathSegment.Builder builder =
        Seg.PathSegment.newBuilder().setSegmentInfo(info.toByteString());
    //    for (Seg.ASEntry entry : entries) {
    //      builder.addAsEntries(entry);
    //    }
    builder.addAllAsEntries(Arrays.asList(entries));
    return builder.build();
  }

  private static Seg.SegmentsResponse buildResponse(
      Seg.SegmentType type, Seg.PathSegment... paths) {
    Seg.SegmentsResponse.Builder replyBuilder = Seg.SegmentsResponse.newBuilder();
    Seg.SegmentsResponse.Segments segments =
        Seg.SegmentsResponse.Segments.newBuilder().addAllSegments(Arrays.asList(paths)).build();
    replyBuilder.putSegments(type.getNumber(), segments);
    return replyBuilder.build();
  }

  private static Timestamp now() {
    Instant now = Instant.now();
    // TODO correct? Set nanos?
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }
}
