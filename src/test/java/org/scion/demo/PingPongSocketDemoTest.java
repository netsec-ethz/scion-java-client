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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.scion.ScionService;
import org.scion.testutil.MockDNS;

public class PingPongSocketDemoTest {

  @BeforeAll
  public static void beforeAll() {
    ScionService.closeDefault();
  }

  @AfterAll
  public static void afterAll() {
    DemoTopology.shutDown();
    MockDNS.clear();
    ScionService.closeDefault();
  }

  @Test
  void test() throws InterruptedException {
    AtomicInteger failures = new AtomicInteger();
    PingPongSocketServer.PRINT = false;
    PingPongSocketClient.PRINT = false;
    Thread server =
        new Thread(
            () -> {
              try {
                PingPongSocketServer.main(null);
              } catch (Throwable e) {
                failures.incrementAndGet();
                throw new RuntimeException(e);
              }
            });
    server.start();

    // Yes, there is a race condition because client may send a packet before
    // the server is ready. Let's fix if it actually happens.
    Thread client =
        new Thread(
            () -> {
              try {
                PingPongSocketClient.main(null);
              } catch (Throwable e) {
                failures.incrementAndGet();
                throw new RuntimeException(e);
              }
            });
    client.start();

    server.join(1000);
    client.join(1000);
    // just in case
    server.interrupt();
    client.interrupt();
    // join again to make sure the interrupt was handled
    server.join(100);
    client.join(100);

    assertEquals(0, failures.get());
  }
}
