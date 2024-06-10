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
import org.scion.jpan.proto.daemon.Daemon;

/**
 * A RequestPath is a Path with additional meta information such as bandwidth, latency or geo
 * coordinates. RequestPaths are created/returned by the ScionService when requesting a new path
 * from the control service.
 *
 * <p>A RequestPath is immutable except for an atomic reference to PathDetails (PathDetails are
 * immutable). The reference may be updated, for example when a path expires.
 */
public interface RequestPath extends Path {

  static RequestPath create(Daemon.Path path, long dstIsdAs, InetAddress dstIP, int dstPort) {
    return RequestPathImpl.create(path, dstIsdAs, dstIP, dstPort);
  }

  PathDetails getDetails();
}
