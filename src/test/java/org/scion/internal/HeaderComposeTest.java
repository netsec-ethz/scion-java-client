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

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.scion.Scion;
import org.scion.ScionService;
import org.scion.ScionUtil;
import org.scion.testutil.ExamplePacket;
import org.scion.testutil.MockDaemon;

public class HeaderComposeTest {

  // Recorded before sending a packet
  private static final byte[] packetBytes = ExamplePacket.PACKET_BYTES_CLIENT_E2E_PING;

  private static MockDaemon daemon;
  private Scion.CloseableService pathService = null;

  @BeforeAll
  public static void beforeAll() throws IOException {
    daemon = MockDaemon.create().start();
  }

  @AfterAll
  public static void afterAll() throws IOException {
    if (daemon != null) {
      daemon.close();
    }
  }

  @AfterEach
  public void afterEach() {
    // path service
    if (pathService != null) {
      try {
        pathService.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      pathService = null;
    }
  }

  /** Compose a packet from scratch. */
  @Test
  public void testCompose() throws IOException {
    MockDaemon.getAndResetCallCount(); // reset counter
    ByteBuffer p = ByteBuffer.allocate(500);

    // User side
    String hostname = "::1";
    int dstPort = 8080;
    long dstIA = ScionUtil.parseIA("1-ff00:0:112");
    String msg = "Hello scion";
    ByteBuffer userPacket = ByteBuffer.allocate(msg.length());
    userPacket.put(msg.getBytes());

    // Socket internal - compose header data
    pathService = Scion.newServiceForAddress(MockDaemon.DEFAULT_ADDRESS_STR);
    long srcIA = pathService.getLocalIsdAs();
    byte[] path =
        ScionService.defaultService().getPath(ScionUtil.parseIA("1-ff00:0:112")).getRawPath();

    InetAddress srcAddress = InetAddress.getByName("127.0.0.1");
    InetAddress dstAddress = InetAddress.getByName(hostname);

    // Socket internal = write header
    ScionHeaderParser.write(
        p, userPacket.limit(), path.length, srcIA, srcAddress, dstIA, dstAddress);
    ScionHeaderParser.writePath(p, path);

    // Pseudo header
    ScionHeaderParser.writeUdpOverlayHeader(p, userPacket.limit(), 44444, dstPort);

    // add payload
    p.put(userPacket);
    p.flip();

    assertTrue(p.limit() > 50);
    for (int i = 0; i < p.limit(); i++) {
      if (i >= 54 && i <= 59) {
        // ignore segID field and timestamp.
        continue;
      }
      if (i >= 66 && i <= 71) {
        // ignore MAC #1
        continue;
      }
      if (i >= 78 && i <= 83) {
        // ignore MAC #2
        continue;
      }
      if (i >= 90 && i <= 91) {
        // ignore UDP checksum
        continue;
      }
      assertEquals(packetBytes[i], p.get(i), "Mismatch at position " + i);
    }
    assertEquals(2, MockDaemon.getAndResetCallCount());
  }
}
