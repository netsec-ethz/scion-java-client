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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import org.xbill.DNS.*;

public class DNSUtil {

  public static void installNAPTR(String asHost, byte[] topoAddr, int topoPort) throws IOException {

    // NAPTR:
    // flags=A
    // service=x-sciondiscovery:tcp
    // regExp=
    // order=1
    // pref=1
    // repl=netsec-w37w3w.inf.ethz.ch.
    // addName=netsec-w37w3w.inf.ethz.ch.
    // dClass=1
    // name=inf.ethz.ch.
    // ttl=2533

    String AS_HOST = asHost; // "iinf.ethz.ch.";
    String REPL_HOST = "topohost.x.y."; // "netsec-w37w3w.inf.ethz.ch."
    Cache c = new Cache(DClass.IN);

    Name name = Name.fromString(AS_HOST + ".");
    Name replacement = new Name(REPL_HOST);

    TXTRecord txt = new TXTRecord(name, DClass.IN, 5000, "x-sciondiscovery=" + topoPort);
    c.addRecord(txt, 10);

    InetAddress addr = InetAddress.getByAddress(topoAddr);
    String naptrFlag;
    if (addr instanceof Inet4Address) {
      ARecord a = new ARecord(replacement, DClass.IN, 5000, addr);
      c.addRecord(a, 10);
      naptrFlag = "A";
    } else {
      AAAARecord a = new AAAARecord(replacement, DClass.IN, 5000, addr);
      c.addRecord(a, 10);
      naptrFlag = "AAAA";
    }

    NAPTRRecord nr2 =
        new NAPTRRecord(
            name, DClass.IN, 5000, 1, 1, naptrFlag, "x-sciondiscovery:tcp", "", replacement);
    c.addRecord(nr2, 10);

    Lookup.setDefaultCache(c, DClass.IN);
  }

  public static void clear() {
    Lookup.setDefaultCache(new Cache(), DClass.IN);
  }
}
