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

package org.scion.demo;

import org.scion.DatagramChannel;
import org.scion.ScionAddress;
import org.scion.ScionSocketAddress;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class ScionPingPongChannelClient {

  private static String extractMessage(ByteBuffer buffer) {
    buffer.flip();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return new String(bytes);
  }

  public static DatagramChannel startClient() throws IOException {
    DatagramChannel client = DatagramChannel.open().bind(null);
    client.configureBlocking(false);
    return client;
  }

  public static void sendMessage(DatagramChannel client, String msg, InetSocketAddress serverAddress)
      throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
    client.send(buffer, serverAddress);
    System.out.println("Sent to server at: " + serverAddress + "  message: " + msg);
  }

  public static String receiveMessage(DatagramChannel channel) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    SocketAddress remoteAddress = channel.receive(buffer);
    if (remoteAddress == null) {
      return null;
    }
    String message = extractMessage(buffer);
    System.out.println("Received from server at: " + remoteAddress + "  message: " + message);
    return message;
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    DemoTopology.configureMock();//Tiny111_112();
    DatagramChannel channel = startClient();
    String msg = "Hello scion";
    //InetSocketAddress serverAddress = new InetSocketAddress("localhost", 44444);
    InetSocketAddress serverAddress = new InetSocketAddress("::1", 44444);
    channel.setDstIsdAs("1-ff00:0:112");
    ScionSocketAddress scionAddress = ScionSocketAddress.create("1-ff00:0:112", "::1", 44444);

    sendMessage(channel, msg, serverAddress);

    boolean finished = false;
    System.out.println("Waiting ...");
    while (!finished) {
      String msg2 = receiveMessage(channel);
      if (msg2 != null && !msg2.isEmpty()) {
        finished = true;
      }
      Thread.sleep(10);
    }
    channel.disconnect();
  }
}
