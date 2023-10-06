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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.scion.testutil.MockNetwork;

public class DatagramChannelPingPongTest {

  private static final int N_REPEAT = 5;
  private static final String MSG = "Hello world!";

  private int nClient = 0;
  private int nServer = 0;

  @Test
  public void testPingPong() throws InterruptedException {
    MockNetwork.startTiny();

    InetSocketAddress serverAddress = new InetSocketAddress("127.0.0.1", 22233);
    Thread server = new Thread(() -> server(serverAddress), "Server-thread");
    server.start();
    Thread client = new Thread(() -> client(serverAddress), "Client-thread");
    client.start();

    client.join();
    server.join();

    MockNetwork.stopTiny();

    assertEquals(N_REPEAT, nClient);
    assertEquals(N_REPEAT, nServer);
  }

  private void client(SocketAddress serverAddress) {
    try {
      ScionDatagramChannel channel = new ScionDatagramChannel();
      channel.setDstIsdAs("1-ff00:0:112");

      for (int i = 0; i < N_REPEAT; i++) {
        ByteBuffer sendBuf = ByteBuffer.wrap(MSG.getBytes());
        channel.send(sendBuf, serverAddress);

        // System.out.println("CLIENT: Receiving ... (" + channel.getLocalAddress() + ")");
        ByteBuffer response = ByteBuffer.allocate(512);
        SocketAddress addr;
        do {
          addr = channel.receive(response);
          Thread.sleep(10); // TODO use Selector
        } while (addr == null);

        response.flip();
        String pong = Charset.defaultCharset().decode(response).toString();
        assertEquals(MSG, pong);

        nClient++;
      }
    } catch (IOException e) {
      System.out.println("CLIENT: I/O error: " + e.getMessage());
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void server(InetSocketAddress localAddress) {
    try {
      ScionDatagramChannel channel = new ScionDatagramChannel().bind(localAddress);
      assertEquals(localAddress, channel.getLocalAddress());
      service(channel);
    } catch (IOException ex) {
      System.out.println("SERVER: I/O error: " + ex.getMessage());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void service(ScionDatagramChannel channel) throws IOException, InterruptedException {
    for (int i = 0; i < N_REPEAT; i++) {
      ByteBuffer request = ByteBuffer.allocate(512);
      // System.out.println("SERVER: --- USER - Waiting for packet --------------------- " +
      // channel.getLocalAddress());
      SocketAddress addr;
      do {
        // TODO what does JDK channel do here if bind() was not called?
        addr = channel.receive(request);
        Thread.sleep(10); // TODO use Selector
      } while (addr == null);

      request.flip();
      String msg = Charset.defaultCharset().decode(request).toString();
      assertEquals(MSG, msg);

      // System.out.println("SERVER: --- USER - Sending packet ----------------------");
      // TODO test that the port is NOT ignored.
      request.flip();
      channel.send(request, addr);
      nServer++;
    }
  }
}