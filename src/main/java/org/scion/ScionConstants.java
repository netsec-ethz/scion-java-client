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

public final class ScionConstants {
  public static final String PROPERTY_DAEMON_HOST = "org.scion.daemon.host";
  public static final String ENV_DAEMON_HOST = "SCION_DAEMON_HOST";
  public static final String DEFAULT_DAEMON_HOST = "localhost";
  public static final String PROPERTY_DAEMON_PORT = "org.scion.daemon.port";
  public static final String ENV_DAEMON_PORT = "SCION_DAEMON_PORT";
  public static final String DEFAULT_DAEMON_PORT = "30255";

  /**
   * Non-public property that allows specifying DNS entries for debugging Example with two entries:
   * server1.com="scion=1-ff00:0:110,127.0.0.1";server2.ch="scion=1-ff00:0:112,::1"
   */
  static final String DEBUG_PROPERTY_DNS_MOCK = "DEBUG_SCION_DNS_MOCK";
}
