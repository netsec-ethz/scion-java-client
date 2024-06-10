// Copyright 2024 ETH Zurich
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

import java.net.InetSocketAddress;

public class ScionResponseAddress extends InetSocketAddress {

  private final ResponsePath responsePath;

  public static ScionResponseAddress from(ResponsePath path) {
    return new ScionResponseAddress(path);
  }

  private ScionResponseAddress(ResponsePath path) {
    super(path.getRemoteAddress(), path.getRemotePort());
    this.responsePath = path;
  }

  public ResponsePath getPath() {
    return responsePath;
  }
}
