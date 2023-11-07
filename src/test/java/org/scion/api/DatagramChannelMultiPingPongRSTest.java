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

package org.scion.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.scion.DatagramChannel;
import org.scion.ScionSocketAddress;

/**
 * Tess receive()/send() operations on DatagramChannel.
 */
class DatagramChannelMultiPingPongRSTest {

  private static final String MSG = "Hello world!";

  @Test
  public void test() {
    PingPongHelper.ServerEndPoint serverFn = this::server;
    PingPongHelper.ClientEndPoint clientFn = this::client;
    PingPongHelper pph = new PingPongHelper(1, 20, 50);
    pph.runPingPong(serverFn, clientFn);
  }

  private void client(DatagramChannel channel, ScionSocketAddress serverAddress, int id)
      throws IOException {
    String message = MSG + "-" + id;
    ByteBuffer sendBuf = ByteBuffer.wrap(message.getBytes());
    channel.send(sendBuf, serverAddress);

    // System.out.println("CLIENT: Receiving ... (" + channel.getLocalAddress() + ")");
    ByteBuffer response = ByteBuffer.allocate(512);
    ScionSocketAddress address = channel.receive(response);
    assertNotNull(address);
    assertEquals(serverAddress.getAddress(), address.getAddress());
    assertEquals(serverAddress.getPort(), address.getPort());

    response.flip();
    String pong = Charset.defaultCharset().decode(response).toString();
    assertEquals(message, pong);
  }

  private void server(DatagramChannel channel) throws IOException {
    ByteBuffer request = ByteBuffer.allocate(512);
    // System.out.println("SERVER: --- USER - Waiting for packet --------------------- " + i);
    SocketAddress address = channel.receive(request);

    request.flip();
    String msg = Charset.defaultCharset().decode(request).toString();
    assertTrue(msg.startsWith(MSG));
    assertTrue(MSG.length() + 3 >= msg.length());

    // System.out.println("SERVER: --- USER - Sending packet ---------------------- " + i);
    request.flip();
    channel.send(request, address);
  }
}
