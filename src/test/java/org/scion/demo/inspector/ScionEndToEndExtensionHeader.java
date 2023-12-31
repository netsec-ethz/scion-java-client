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

package org.scion.demo.inspector;

import static org.scion.demo.inspector.ByteUtil.*;

import java.nio.ByteBuffer;

public class ScionEndToEndExtensionHeader {

  // 8 bit
  private int nextHdr;
  // 8 bit
  private int extLen;
  private int extLenBytes;
  // 48 bit
  private long options;

  public void read(ByteBuffer data) {
    long l0 = data.getInt();
    nextHdr = (int) readLong(l0, 0, 8);
    extLen = (int) readLong(l0, 8, 8);
    extLenBytes = (extLen + 1) * 4;
    options = readLong(l0, 16, 48);
    // TODO validate checksum
  }

  @Override
  public String toString() {
    return "ScionEndToEndExtensionHeader{"
        + "nextHdr="
        + nextHdr
        + ", extLen="
        + extLen
        + ", options="
        + options
        + '}';
  }

  public Constants.HdrTypes nextHdr() {
    return Constants.HdrTypes.parse(nextHdr);
  }

  public int getExtLenBytes() {
    return extLenBytes;
  }
}
