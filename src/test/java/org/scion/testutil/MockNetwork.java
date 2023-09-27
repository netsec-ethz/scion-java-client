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

package org.scion.testutil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class MockNetwork {

    private static final Logger logger = LoggerFactory.getLogger(MockNetwork.class.getName());

  private static ForkJoinPool pool = new ForkJoinPool();

    private static Thread runner;

    public static void startTiny(int br1Port, int br2Port, InetSocketAddress br1Dst, InetSocketAddress br2Dst) {
//        if (pool.getPoolSize() != 0) {
//            throw new IllegalStateException();
//        }
        if (runner != null) {
            throw new IllegalStateException();
        }

      //pool.execute(new MockBorderRouter("Bridge-1", br1Port, br2Port, br1Dst, br2Dst));
        runner = new Thread(new MockBorderRouter("Bridge-1", br1Port, br2Port, br1Dst, br2Dst));
        runner.start();
    }

    public static void stopTiny() {
        try {
            logger.warn("Shutting down daemon");
//            pool.shutdown();
//            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
//                throw new IllegalStateException("Threads did not terminate!");
//            }
            runner.interrupt();
            runner.join();
            runner = null;
            logger.warn("Daemon shut down");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class MockBorderRouter implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MockBorderRouter.class.getName());

    private final String name;
    private final int port1;
    private final int port2;
    private final InetSocketAddress dstAddr1;
    private final InetSocketAddress dstAddr2;
  private DatagramChannel in;
  private DatagramChannel out;


  MockBorderRouter(String name, int port1, int port2, InetSocketAddress dstAddr1, InetSocketAddress dstAddr2) {
    this.name = name;
      this.port1 = port1;
      this.port2 = port2;
      this.dstAddr1 = dstAddr1;
      this.dstAddr2 = dstAddr2;
  }

  @Override
  public void run() {
      System.out.println("Running " + name + " on port " + port1 + " -> " + dstAddr1);
      System.out.println("    and " + name + " on port " + port2 + " -> " + dstAddr2);
    try {
        in = DatagramChannel.open().bind(new InetSocketAddress("localhost", port1));
        out = DatagramChannel.open().bind(new InetSocketAddress("localhost", port2));
        in.configureBlocking(false);
        out.configureBlocking(false);
        // TODO use selectors, see e.g. https://www.baeldung.com/java-nio-selector
        while (true) {
            ByteBuffer bb = ByteBuffer.allocateDirect(65000);
            SocketAddress a1 = in.receive(bb);
            if (a1 != null) {
                logger.info("Service " + name + " sending to " + dstAddr1 + "... ");
                bb.flip();
                out.send(bb, dstAddr1);
            }
            SocketAddress a2 = out.receive(bb);
            if (a2 != null) {
                logger.info("Service " + name + " sending to " + dstAddr2 + "... ");
                bb.flip();
                in.send(bb, dstAddr2);
            }
            Thread.sleep(100);

      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
      if (in != null) {
          try {
              in.close();
          } catch (IOException e) {
              throw new RuntimeException(e);
          }
      }
      if (out != null) {
          try {
              out.close();
          } catch (IOException e) {
              throw new RuntimeException(e);
          }
      }
    }
  }
}
