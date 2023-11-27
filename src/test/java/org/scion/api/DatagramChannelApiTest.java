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
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.charset.Charset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scion.*;
import org.scion.testutil.ExamplePacket;
import org.scion.testutil.MockDNS;
import org.scion.testutil.MockDaemon;
import org.scion.testutil.PingPongHelper;

class DatagramChannelApiTest {

  private static final int dummyPort = 44444;
  private static final String MSG = "Hello scion!";

  @BeforeEach
  public void beforeAll() throws IOException {
    MockDaemon.createAndStartDefault();
  }

  @AfterEach
  public void afterAll() throws IOException {
    MockDaemon.closeDefault();
  }

  @Test
  void getLocalAddress_withBind() throws IOException {
    InetSocketAddress addr = new InetSocketAddress("localhost", dummyPort);
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
  void send_requiresAddressWithScionTxt() {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    InetSocketAddress addr = new InetSocketAddress("1.1.1.1", 30255);
    try (DatagramChannel channel = DatagramChannel.open()) {
      Exception ex = assertThrows(IOException.class, () -> channel.send(buffer, addr));
      assertTrue(ex.getMessage().contains("No DNS TXT entry found for host"), ex.getMessage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void send_requiresAddressWithScionCorrectTxt() {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    String TXT = "\"XXXscion=1-ff00:0:110,127.0.0.55\"";
    System.setProperty(PackageVisibilityHelper.DEBUG_PROPERTY_DNS_MOCK, "127.0.0.55=" + TXT);
    InetSocketAddress addr = new InetSocketAddress("127.0.0.55", 30255);
    try (DatagramChannel channel = DatagramChannel.open()) {
      Exception ex = assertThrows(IOException.class, () -> channel.send(buffer, addr));
      assertTrue(ex.getMessage().contains("Invalid TXT entry"), ex.getMessage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      System.clearProperty(PackageVisibilityHelper.DEBUG_PROPERTY_DNS_MOCK);
    }
  }

  @Test
  void isOpen() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertTrue(channel.isOpen());
      channel.close();
      assertFalse(channel.isOpen());
    }
  }

  @Test
  void isBlocking() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertTrue(channel.isOpen());
      channel.close();
      assertFalse(channel.isOpen());
    }
  }

  @Test
  public void isConnected_InetSocket() throws IOException {
    MockDNS.install("1-ff00:0:112", "ip6-localhost", "::1");
    InetSocketAddress address = new InetSocketAddress("::1", 12345);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertFalse(channel.isConnected());
      channel.connect(address);
      assertTrue(channel.isConnected());

      // try connecting again
      // Should be AlreadyConnectedException, but Temurin throws IllegalStateException
      Exception ex = assertThrows(IllegalStateException.class, () -> channel.connect(address));
      // assertNull(ex.getMessage(), ex.getMessage());
      assertTrue(channel.isConnected());

      // disconnect
      channel.disconnect();
      assertFalse(channel.isConnected());
      channel.disconnect();
      assertFalse(channel.isConnected());

      // Connect again
      channel.connect(address);
      assertTrue(channel.isConnected());
      channel.close();
      assertFalse(channel.isConnected());
    } finally {
      MockDNS.clear();
    }
  }

  @Test
  public void isConnected_Path() throws IOException {
    Path path = PackageVisibilityHelper.createDummyPath();
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertFalse(channel.isConnected());
      channel.connect(path);
      assertTrue(channel.isConnected());

      // try connecting again
      // Should be AlreadyConnectedException, but Temurin throws IllegalStateException
      Exception ex = assertThrows(IllegalStateException.class, () -> channel.connect(path));
      // assertNull(ex.getMessage(), ex.getMessage());
      assertTrue(channel.isConnected());

      // disconnect
      channel.disconnect();
      assertFalse(channel.isConnected());
      channel.disconnect();
      assertFalse(channel.isConnected());

      // Connect again
      channel.connect(path);
      assertTrue(channel.isConnected());
      channel.close();
      assertFalse(channel.isConnected());
    }
  }

  @Test
  public void getService() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertEquals(Scion.defaultService(), channel.getService());
      ScionService service2 = Scion.newServiceForAddress("127.0.0.2");
      channel.setService(service2);
      assertEquals(service2, channel.getService());
      // TODO test that the service is actually used
    }
  }

  @Test
  public void getPathPolicy() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertEquals(PathPolicy.DEFAULT, channel.getPathPolicy());
      assertEquals(PathPolicy.FIRST, channel.getPathPolicy());
      channel.setPathPolicy(PathPolicy.MIN_HOPS);
      assertEquals(PathPolicy.MIN_HOPS, channel.getPathPolicy());
      // TODO test that path policy is actually used
    }
  }

  @Test
  public void receive_bufferTooSmall() throws IOException {
    MockDaemon.closeDefault(); // We don't need the daemon here
    PingPongHelper.ServerEndPoint serverFn = this::defaultServer;
    PingPongHelper.ClientEndPoint clientFn =
        (channel, serverAddress, id) -> {
          String message = MSG + "-" + id;
          ByteBuffer sendBuf = ByteBuffer.wrap(message.getBytes());
          channel.send(sendBuf, serverAddress);

          // System.out.println("CLIENT: Receiving ... (" + channel.getLocalAddress() + ")");
          ByteBuffer response = ByteBuffer.allocate(5);
          channel.receive(response);

          response.flip();
          assertEquals(5, response.limit());
          String pong = Charset.defaultCharset().decode(response).toString();
          assertEquals(message.substring(0, 5), pong);
        };
    PingPongHelper pph = new PingPongHelper(1, 1, 1);
    pph.runPingPong(serverFn, clientFn);
  }

  @Test
  public void read_bufferTooSmall() throws IOException {
    MockDaemon.closeDefault(); // We don't need the daemon here
    PingPongHelper.ServerEndPoint serverFn = this::defaultServer;
    PingPongHelper.ClientEndPoint clientFn =
        (channel, serverAddress, id) -> {
          String message = MSG + "-" + id;
          ByteBuffer sendBuf = ByteBuffer.wrap(message.getBytes());
          channel.connect(serverAddress);
          channel.write(sendBuf);

          // System.out.println("CLIENT: Receiving ... (" + channel.getLocalAddress() + ")");
          ByteBuffer response = ByteBuffer.allocate(5);
          int len = channel.read(response);
          assertEquals(5, len);

          response.flip();
          String pong = Charset.defaultCharset().decode(response).toString();
          assertEquals(message.substring(0, 5), pong);
        };
    PingPongHelper pph = new PingPongHelper(1, 1, 1);
    pph.runPingPong(serverFn, clientFn);
  }

  private void defaultServer(DatagramChannel channel) throws IOException {
    ByteBuffer request = ByteBuffer.allocate(512);
    // System.out.println("SERVER: --- USER - Waiting for packet --------------------- " + i);
    Path address = channel.receive(request);

    request.flip();
    String msg = Charset.defaultCharset().decode(request).toString();
    assertTrue(msg.startsWith(MSG), msg);
    assertTrue(MSG.length() + 3 >= msg.length());

    // System.out.println("SERVER: --- USER - Sending packet ---------------------- " + i);
    request.flip();
    channel.send(request, address);
  }

  @Test
  public void send_bufferTooLarge() {
    Path addr = ExamplePacket.PATH;
    ByteBuffer buffer = ByteBuffer.allocate(65500);
    buffer.limit(buffer.capacity());
    try (DatagramChannel channel = DatagramChannel.open()) {
      Exception ex = assertThrows(IOException.class, () -> channel.send(buffer, addr));
      assertTrue(ex.getMessage().contains("Message too long"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void write_bufferToLarge() {
    Path addr = ExamplePacket.PATH;
    ByteBuffer buffer = ByteBuffer.allocate(65500);
    buffer.limit(buffer.capacity());
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.connect(addr);
      Exception ex = assertThrows(IOException.class, () -> channel.write(buffer));
      assertTrue(ex.getMessage().contains("Message too long"), ex.getMessage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void read_NotConnectedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertThrows(NotYetConnectedException.class, () -> channel.read(buffer));
    }
  }

  @Test
  public void read_ChannelClosedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.close();
      assertThrows(ClosedChannelException.class, () -> channel.read(buffer));
    }
  }

  @Test
  public void write_NotConnectedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertThrows(NotYetConnectedException.class, () -> channel.write(buffer));
    }
  }

  @Test
  public void write_ChannelClosedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.close();
      assertThrows(ClosedChannelException.class, () -> channel.write(buffer));
    }
  }
}
