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

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.scion.ScionSocketAddress;
import org.scion.ScionUtil;
import org.scion.testutil.ExamplePacket;

public class HeaderParser2Test {

  // Original incoming packet
  private static final byte[] packetBytes = ExamplePacket.PACKET_BYTES_SERVER_E2E_PING;

  // reversed packet
  private static final byte[] reversedBytes = ExamplePacket.PACKET_BYTES_SERVER_E2E_PONG;

  /**
   * Parse and re-serialize the packet. The generated content should be identical to the original
   * content
   */
  @Test
  public void testParse() {
    ByteBuffer buffer = ByteBuffer.wrap(packetBytes);
    InetSocketAddress firstHop = new InetSocketAddress("127.0.0.42", 23456); // TODO test

    ByteBuffer userRcvBuffer = ByteBuffer.allocate(10000);
    ScionHeaderParser.readUserData(buffer, userRcvBuffer);
    ScionSocketAddress remoteAddr = ScionHeaderParser.readRemoteSocketAddress(buffer, firstHop);
    userRcvBuffer.flip();

    // payload
    byte[] payloadBytes = new byte[userRcvBuffer.remaining()];
    userRcvBuffer.get(payloadBytes);
    String payload = new String(payloadBytes);
    assertEquals("Hello scion", payload);

    // remote address
    assertEquals(44444, remoteAddr.getPort());
    assertEquals("localhost", remoteAddr.getHostName());
    assertEquals("1-ff00:0:110", ScionUtil.toStringIA(remoteAddr.getIsdAs()));
    assertEquals(firstHop, remoteAddr.getPath().getFirstHopAddress());

    // path
    byte[] path = remoteAddr.getPath().getRawPath();
    assertEquals(36, path.length);
    for (int i = 0; i < path.length; i++) {
      assertEquals(reversedBytes[i + 48], path[i], "At position:" + i);
    }
  }
}
