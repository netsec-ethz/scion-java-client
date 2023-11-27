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

import java.net.InetSocketAddress;

public class ResponsePath extends Path {

  private final InetSocketAddress firstHopAddress;

  @Deprecated // TODO use otehr create()
  public static ResponsePath create(
          byte[] rawPath,
          long srcIsdAs, // TODO remove these
          byte[] srcIP,
          int srcPort,
          long dstIsdAs,
          byte[] dstIP,
          int dstPort,
          InetSocketAddress firstHopAddress) {
    return new ResponsePath(rawPath, dstIsdAs, dstIP, dstPort, firstHopAddress);
  }

  public static ResponsePath create(
          byte[] rawPath,
          long dstIsdAs,
          byte[] dstIP,
          int dstPort,
          InetSocketAddress firstHopAddress) {
    return new ResponsePath(rawPath, dstIsdAs, dstIP, dstPort, firstHopAddress);
  }

  private ResponsePath(
      byte[] rawPath,
      long dstIsdAs,
      byte[] dstIP,
      int dstPort,
      InetSocketAddress firstHopAddress) {
    super(rawPath, dstIsdAs, dstIP, dstPort);
    this.firstHopAddress = firstHopAddress;
  }

  @Override
  public InetSocketAddress getFirstHopAddress() {
    return firstHopAddress;
  }
}