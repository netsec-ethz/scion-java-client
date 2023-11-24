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

package org.scion;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;

import com.google.protobuf.ByteString;
import org.scion.internal.ScionHeaderParser;
import org.scion.proto.daemon.Daemon;

/**
 * Helper class to access package private methods in org.scion.ScionService and ScionPacketHelper.
 */
public class PackageVisibilityHelper {

  public static final String DEBUG_PROPERTY_DNS_MOCK = ScionConstants.DEBUG_PROPERTY_DNS_MOCK;

  public static List<Daemon.Path> getPathList(ScionService service, long srcIsdAs, long dstIsdAs)
      throws ScionException {
    return service.getPathList(srcIsdAs, dstIsdAs);
  }

  public static InetSocketAddress getDstAddress(ByteBuffer packet) throws UnknownHostException {
    return ScionHeaderParser.readDestinationSocketAddress(packet);
  }

  public static Path createDummyPath() {
    String ip = null;
    try {
      ip = Inet4Address.getLocalHost().toString();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    return createDummyPath(0, ip, 55555, new byte[0], new InetSocketAddress(12345));
  }

  public static RequestPath createDummyPath(
      long dstIsdAs, String dstHost, int dstPort, byte[] raw, InetSocketAddress firstHop) {
    ByteString bs = ByteString.copyFrom(raw);
    Daemon.Interface inter =
        Daemon.Interface.newBuilder()
            .setAddress(Daemon.Underlay.newBuilder().setAddress(firstHop.toString().substring(1)).build())
            .build();
    Daemon.Path path = Daemon.Path.newBuilder().setRaw(bs).setInterface(inter).build();
    return RequestPath.create(path, dstIsdAs, dstHost, dstPort);
  }
}
