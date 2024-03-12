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

import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scion.*;
import org.scion.proto.daemon.Daemon;
import org.scion.testutil.ExamplePacket;
import org.scion.testutil.MockDNS;
import org.scion.testutil.MockDaemon;
import org.scion.testutil.PingPongHelper;

class DatagramChannelApiTest {

  private static final int dummyPort = 44444;

  @BeforeEach
  public void beforeEach() throws IOException {
    MockDaemon.createAndStartDefault();
  }

  @AfterEach
  public void afterEach() throws IOException {
    MockDaemon.closeDefault();
    MockDNS.clear();
  }

  @AfterAll
  public static void afterAll() {
    // Defensive clean up
    ScionService.closeDefault();
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
  void getLocalAddress_notLocalhost() throws IOException {
    ScionService pathService = Scion.defaultService();
    // TXT entry: "scion=64-2:0:9,129.132.230.98"
    ScionAddress sAddr = pathService.getScionAddress("ethz.ch");
    InetSocketAddress firstHop = new InetSocketAddress("1.1.1.1", dummyPort);

    RequestPath path =
        PackageVisibilityHelper.createDummyPath(
            sAddr.getIsdAs(),
            sAddr.getInetAddress().getAddress(),
            dummyPort,
            new byte[100],
            firstHop);

    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.connect(path);
      // Assert that this resolves to a non-local address!
      assertFalse(channel.getLocalAddress().toString().contains("127.0.0."));
      assertFalse(channel.getLocalAddress().toString().contains("0:0:0:0:0:0:0:0"));
      assertFalse(channel.getLocalAddress().toString().contains("0:0:0:0:0:0:0:1"));
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
      assertTrue(ex.getMessage().contains("No DNS TXT entry \"scion\" found"), ex.getMessage());
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
  void isBlocking_default() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertTrue(channel.isBlocking());
    }
  }

  @Test
  void isBlocking_true_read() throws IOException, InterruptedException {
    testBlocking(true, channel -> channel.read(ByteBuffer.allocate(100)));
  }

  @Test
  void isBlocking_false_read() throws IOException, InterruptedException {
    testBlocking(false, channel -> channel.read(ByteBuffer.allocate(100)));
  }

  @Test
  void isBlocking_true_receiver() throws IOException, InterruptedException {
    testBlocking(true, channel -> channel.receive(ByteBuffer.allocate(100)));
  }

  @Test
  void isBlocking_false_receive() throws IOException, InterruptedException {
    testBlocking(false, channel -> channel.receive(ByteBuffer.allocate(100)));
  }

  interface ChannelConsumer {
    void accept(DatagramChannel channel) throws InterruptedException, IOException;
  }

  private void testBlocking(boolean isBlocking, ChannelConsumer fn)
      throws IOException, InterruptedException {
    MockDNS.install("1-ff00:0:112", "localhost", "127.0.0.1");
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean wasBlocking = new AtomicBoolean(true);
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.connect(address);
      channel.configureBlocking(isBlocking);
      assertEquals(isBlocking, channel.isBlocking());
      Thread t =
          new Thread(
              () -> {
                try {
                  latch.countDown();
                  fn.accept(channel);
                  // Should only be reached with non-blocking channel
                  wasBlocking.getAndSet(false);
                } catch (InterruptedException | IOException e) {
                  // ignore
                }
              });
      t.start();
      latch.await();
      t.join(10);
      t.interrupt();
      assertEquals(isBlocking, wasBlocking.get());
    }
  }

  @Test
  void isConnected_InetSocket() throws IOException {
    //    MockDNS.install("1-ff00:0:112", "ip6-localhost", "::1");
    //    InetSocketAddress address = new InetSocketAddress("::1", 12345);
    // We have to use IPv4 because IPv6 fails on GitHubs Ubuntu CI images.
    MockDNS.install("1-ff00:0:112", "localhost", "127.0.0.1");
    InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertFalse(channel.isConnected());
      assertNull(channel.getRemoteAddress());
      channel.connect(address);
      assertTrue(channel.isConnected());
      assertEquals(address, channel.getRemoteAddress());

      // try connecting again
      // Should be AlreadyConnectedException but Temurin throws IllegalStateException
      assertThrows(IllegalStateException.class, () -> channel.connect(address));
      assertTrue(channel.isConnected());

      // disconnect
      channel.disconnect();
      assertFalse(channel.isConnected());
      assertNull(channel.getRemoteAddress());
      channel.disconnect();
      assertFalse(channel.isConnected());

      // Connect again
      channel.connect(address);
      assertTrue(channel.isConnected());
      assertEquals(address, channel.getRemoteAddress());
      channel.close();
      assertFalse(channel.isConnected());
    }
  }

  @Test
  void isConnected_Path() throws IOException {
    RequestPath path = PackageVisibilityHelper.createDummyPath();
    InetAddress ip = InetAddress.getByAddress(path.getDestinationAddress());
    InetSocketAddress address = new InetSocketAddress(ip, path.getDestinationPort());
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertFalse(channel.isConnected());
      assertNull(channel.getRemoteAddress());
      channel.connect(path);
      assertTrue(channel.isConnected());
      assertEquals(address, channel.getRemoteAddress());

      // try connecting again
      // Should be AlreadyConnectedException, but Temurin throws IllegalStateException
      assertThrows(IllegalStateException.class, () -> channel.connect(path));
      assertTrue(channel.isConnected());

      // disconnect
      channel.disconnect();
      assertFalse(channel.isConnected());
      assertNull(channel.getRemoteAddress());
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
  void getService_default() throws IOException {
    ScionService service1 = Scion.defaultService();
    ScionService service2 = Scion.newServiceWithDaemon(MockDaemon.DEFAULT_ADDRESS_STR);
    try (DatagramChannel channel = DatagramChannel.open()) {
      // The initial channel should NOT have a service.
      // A server side channel may never need a service so we shouldn't create it.
      assertNull(channel.getService());

      // trigger service initialization in channel
      RequestPath path = PackageVisibilityHelper.createDummyPath();
      channel.send(ByteBuffer.allocate(0), path);
      assertNotEquals(service2, channel.getService());
      assertEquals(service1, channel.getService());
    }
    service2.close();
  }

  @Test
  void getService_non_default() throws IOException {
    ScionService service1 = Scion.defaultService();
    ScionService service2 = Scion.newServiceWithDaemon(MockDaemon.DEFAULT_ADDRESS_STR);
    try (DatagramChannel channel = DatagramChannel.open(service2)) {
      assertEquals(service2, channel.getService());
      assertNotEquals(service1, channel.getService());
    }
    service2.close();
  }

  @Test
  void getPathPolicy() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertEquals(PathPolicy.DEFAULT, channel.getPathPolicy());
      assertEquals(PathPolicy.MIN_HOPS, channel.getPathPolicy());
      channel.setPathPolicy(PathPolicy.MAX_BANDWIDTH);
      assertEquals(PathPolicy.MAX_BANDWIDTH, channel.getPathPolicy());
      // TODO test that path policy is actually used
    }
  }

  @Test
  void send_bufferTooLarge() {
    RequestPath addr = ExamplePacket.PATH;
    ByteBuffer buffer = ByteBuffer.allocate(65440);
    buffer.limit(buffer.capacity());
    try (DatagramChannel channel = DatagramChannel.open()) {
      Exception ex = assertThrows(IOException.class, () -> channel.send(buffer, addr));
      String msg = ex.getMessage();
      // Linux vs Windows(?)
      assertTrue(msg.contains("too long") || msg.contains("larger than"), ex.getMessage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void write_bufferTooLarge() {
    RequestPath addr = ExamplePacket.PATH;
    ByteBuffer buffer = ByteBuffer.allocate(65440);
    buffer.limit(buffer.capacity());
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.connect(addr);
      Exception ex = assertThrows(IOException.class, () -> channel.write(buffer));
      String msg = ex.getMessage();
      // Linux vs Windows(?)
      assertTrue(msg.contains("too long") || msg.contains("larger than"), ex.getMessage());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void read_NotConnectedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertThrows(NotYetConnectedException.class, () -> channel.read(buffer));
    }
  }

  @Test
  void read_ChannelClosedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.close();
      assertThrows(ClosedChannelException.class, () -> channel.read(buffer));
    }
  }

  @Test
  void write_NotConnectedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertThrows(NotYetConnectedException.class, () -> channel.write(buffer));
    }
  }

  @Test
  void write_ChannelClosedFails() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(100);
    try (DatagramChannel channel = DatagramChannel.open()) {
      channel.close();
      assertThrows(ClosedChannelException.class, () -> channel.write(buffer));
    }
  }

  @Test
  void send_disconnected_expiredRequestPath() throws IOException {
    // Expected behavior: expired paths should be replaced transparently.
    testExpired(
        (channel, expiredPath) -> {
          ByteBuffer sendBuf = ByteBuffer.wrap(PingPongHelper.MSG.getBytes());
          try {
            channel.disconnect();
            RequestPath newPath = (RequestPath) channel.send(sendBuf, expiredPath);
            assertTrue(newPath.getExpiration() > expiredPath.getExpiration());
            assertTrue(Instant.now().getEpochSecond() < newPath.getExpiration());
            // assertNull(channel.getCurrentPath());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void send_connected_expiredRequestPath() throws IOException {
    // Expected behavior: expired paths should be replaced transparently.
    testExpired(
        (channel, expiredPath) -> {
          ByteBuffer sendBuf = ByteBuffer.wrap(PingPongHelper.MSG.getBytes());
          try {
            RequestPath newPath = (RequestPath) channel.send(sendBuf, expiredPath);
            assertTrue(newPath.getExpiration() > expiredPath.getExpiration());
            assertTrue(Instant.now().getEpochSecond() < newPath.getExpiration());
            // assertNull(channel.getCurrentPath());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void write_expiredRequestPath() throws IOException {
    // Expected behavior: expired paths should be replaced transparently.
    testExpired(
        (channel, expiredPath) -> {
          ByteBuffer sendBuf = ByteBuffer.wrap(PingPongHelper.MSG.getBytes());
          try {
            channel.write(sendBuf);
            RequestPath newPath = (RequestPath) channel.getConnectionPath();
            assertTrue(newPath.getExpiration() > expiredPath.getExpiration());
            assertTrue(Instant.now().getEpochSecond() < newPath.getExpiration());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void testExpired(BiConsumer<DatagramChannel, RequestPath> sendMethod) throws IOException {
    MockDaemon.closeDefault(); // We don't need the daemon here
    PingPongHelper.Server serverFn = PingPongHelper::defaultServer;
    PingPongHelper.Client clientFn =
        (channel, basePath, id) -> {
          // Build a path that is already expired
          RequestPath expiredPath = createExpiredPath(basePath);
          sendMethod.accept(channel, expiredPath);

          // System.out.println("CLIENT: Receiving ... (" + channel.getLocalAddress() + ")");
          ByteBuffer response = ByteBuffer.allocate(100);
          channel.receive(response);

          response.flip();
          String pong = Charset.defaultCharset().decode(response).toString();
          assertEquals(PingPongHelper.MSG, pong);
        };
    PingPongHelper pph = new PingPongHelper(1, 10, 5);
    pph.runPingPong(serverFn, clientFn);
  }

  private RequestPath createExpiredPath(Path basePath) throws UnknownHostException {
    long now = Instant.now().getEpochSecond();
    Daemon.Path.Builder builder =
        Daemon.Path.newBuilder().setExpiration(Timestamp.newBuilder().setSeconds(now - 10).build());
    RequestPath expiredPath =
        PackageVisibilityHelper.createRequestPath110_112(
            builder,
            basePath.getDestinationIsdAs(),
            basePath.getDestinationAddress(),
            basePath.getDestinationPort(),
            basePath.getFirstHopAddress());
    assertTrue(Instant.now().getEpochSecond() > expiredPath.getExpiration());
    return expiredPath;
  }

  @Test
  void getConnectionPath() {
    RequestPath addr = ExamplePacket.PATH;
    ByteBuffer buffer = ByteBuffer.allocate(50);
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertNull(channel.getConnectionPath());

      // connect should set a path
      channel.connect(addr);
      assertNotNull(channel.getConnectionPath());
      channel.disconnect();
      assertNull(channel.getConnectionPath());

      // send should NOT set a path
      channel.send(buffer, addr);
      assertNull(channel.getConnectionPath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void setOption() throws IOException {
    try (DatagramChannel channel = DatagramChannel.open()) {
      assertFalse(channel.getOption(ScionSocketOptions.SN_API_THROW_PARSER_FAILURE));
      DatagramChannel dc = channel.setOption(ScionSocketOptions.SN_API_THROW_PARSER_FAILURE, true);
      assertEquals(channel, dc);

      int margin = channel.getOption(ScionSocketOptions.SN_PATH_EXPIRY_MARGIN);
      channel.setOption(ScionSocketOptions.SN_PATH_EXPIRY_MARGIN, margin + 1000);
      assertEquals(margin + 1000, channel.getOption(ScionSocketOptions.SN_PATH_EXPIRY_MARGIN));

      int bufSizeSend = channel.getOption(StandardSocketOptions.SO_SNDBUF);
      channel.setOption(StandardSocketOptions.SO_SNDBUF, bufSizeSend + 1000);
      assertEquals(bufSizeSend + 1000, channel.getOption(StandardSocketOptions.SO_SNDBUF));

      int bufSizeReceive = channel.getOption(StandardSocketOptions.SO_RCVBUF);
      channel.setOption(StandardSocketOptions.SO_RCVBUF, bufSizeReceive + 1000);
      assertEquals(bufSizeReceive + 1000, channel.getOption(StandardSocketOptions.SO_RCVBUF));
    }
  }
}
