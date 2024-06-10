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

package org.scion.jpan;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A ResponsePath is created/returned when receiving a packet. Besides being a Path, it contains
 * ISD/AS, IP and port of the local host. This is mostly for convenience to avoid looking up this
 * information, but it also ensures that the return packet header contains the exact information
 * sent/expected by the client.
 *
 * <p>A ResponsePath is immutable and thus thread safe.
 */
public interface ResponsePath extends Path {

  static ResponsePath create(
      byte[] rawPath,
      long srcIsdAs,
      InetAddress srcIP,
      int srcPort,
      long dstIsdAs,
      InetAddress dstIP,
      int dstPort,
      InetSocketAddress firstHopAddress) {
    return ResponsePathImpl.create(
        rawPath, srcIsdAs, srcIP, srcPort, dstIsdAs, dstIP, dstPort, firstHopAddress);
  }

  long getLocalIsdAs();

  InetAddress getLocalAddress();

  int getLocalPort();
}
