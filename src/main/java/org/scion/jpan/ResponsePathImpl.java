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
class ResponsePathImpl extends PathImpl implements ResponsePath {

  private final InetSocketAddress firstHopAddress;
  // The ResponsePath gets source information from the incoming packet.
  private final long srcIsdAs;
  private final InetAddress srcAddress;
  private final int srcPort;
  private final byte[] pathRaw;

  public static ResponsePathImpl create(
      byte[] rawPath,
      long srcIsdAs,
      InetAddress srcIP,
      int srcPort,
      long dstIsdAs,
      InetAddress dstIP,
      int dstPort,
      InetSocketAddress firstHopAddress) {
    return new ResponsePathImpl(
        rawPath, srcIsdAs, srcIP, srcPort, dstIsdAs, dstIP, dstPort, firstHopAddress);
  }

  private ResponsePathImpl(
      byte[] rawPath,
      long srcIsdAs,
      InetAddress srcIP,
      int srcPort,
      long dstIsdAs,
      InetAddress dstIP,
      int dstPort,
      InetSocketAddress firstHopAddress) {
    super(dstIsdAs, dstIP, dstPort);
    this.firstHopAddress = firstHopAddress;
    this.srcIsdAs = srcIsdAs;
    this.srcAddress = srcIP;
    this.srcPort = srcPort;
    this.pathRaw = rawPath;
  }

  @Override
  public InetSocketAddress getFirstHopAddress() {
    return firstHopAddress;
  }

  public long getLocalIsdAs() {
    return srcIsdAs;
  }

  public InetAddress getLocalAddress() {
    return srcAddress;
  }

  public int getLocalPort() {
    return srcPort;
  }

  @Override
  public byte[] getRawPath() {
    return pathRaw;
  }

  @Override
  public String toString() {
    return "ResponsePath{"
        + super.toString()
        + ", firstHopAddress="
        + firstHopAddress
        + ", localIsdAs="
        + srcIsdAs
        + ", localAddress="
        + srcAddress
        + ", localPort="
        + srcPort
        + '}';
  }
}
