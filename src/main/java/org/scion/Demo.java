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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class Demo {

  public static void main(String[] args) throws SocketException {
    // TODO NetworkInterface??? -> Provide SCION info? Or do it in separate API?
    Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
    while (en.hasMoreElements()) {
      NetworkInterface ni = en.nextElement();
      System.out.println(" Display Name = " + ni.getDisplayName());
      System.out.println(" MTU = " + ni.getMTU());
      Enumeration<InetAddress> addresses = ni.getInetAddresses();
      while (addresses.hasMoreElements()) {
        System.out.println("   Address = " + addresses.nextElement());
      }
    }

    // API for UDP
    DatagramSocket socket;

    // API for PathService
    // TODO rename
    ScionService pathService;

    // TODO -- TCP

    // TODO -- HTTP
    // Java 11: HTTPClient
    // Apache HttpClient, OkHttpClient, Spring WebClient ???
  }
}
