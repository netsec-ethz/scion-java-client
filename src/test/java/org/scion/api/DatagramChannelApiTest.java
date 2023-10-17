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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.scion.DatagramChannel;

class DatagramChannelApiTest {

  @Test
  void getLocalAddress_withBind() throws IOException {
    InetSocketAddress addr = new InetSocketAddress("localhost", 44444);
    try (DatagramChannel channel = DatagramChannel.open().bind(addr)) {
      assertEquals(addr, channel.getLocalAddress());
    }
  }

  @Test
  void getLocalAddress_withoutBind() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertNull(channel.getLocalAddress());
    }
  }

  @Test
  void send_RequiresInetSocketAddress() throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(100);
    Exception exception;
    try (DatagramChannel channel = DatagramChannel.open()) {
      SocketAddress addr =
          new SocketAddress() {
            @Override
            public int hashCode() {
              return super.hashCode();
            }
          };
      exception = assertThrows(IllegalArgumentException.class, () -> channel.send(bb, addr));
    }

    String expectedMessage = "must be of type InetSocketAddress";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  void sendPath_RequiresInetSocketAddress() throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(100);
    Exception exception;
    try (DatagramChannel channel = DatagramChannel.open()) {
      SocketAddress addr =
          new SocketAddress() {
            @Override
            public int hashCode() {
              return super.hashCode();
            }
          };
      exception = assertThrows(IllegalArgumentException.class, () -> channel.send(bb, addr, null));
    }

    String expectedMessage = "must be of type InetSocketAddress";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }
}