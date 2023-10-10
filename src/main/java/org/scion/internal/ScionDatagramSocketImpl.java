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

package org.scion.internal;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class ScionDatagramSocketImpl extends DatagramSocketImpl {

  private DatagramChannel channel;

  @Override
  protected void create() throws SocketException {
    try {
      channel = DatagramChannel.open();
    } catch (IOException e) {
      throw new SocketException(e.getMessage());
    }
  }

  @Override
  protected void bind(int port, InetAddress inetAddress) throws SocketException {
    try {
      channel.bind(new InetSocketAddress(inetAddress, port));
    } catch (IOException e) {
      throw new SocketException(e.getMessage());
    }
  }

  @Override
  protected void send(DatagramPacket datagramPacket) throws IOException {
    ByteBuffer buf =
        ByteBuffer.wrap(
            datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength());
    channel.send(buf, datagramPacket.getSocketAddress());
  }

  @Override
  protected int peek(InetAddress inetAddress) throws IOException {
    // inetAddress.
    return 0;
  }

  @Override
  protected int peekData(DatagramPacket datagramPacket) throws IOException {
    return 0;
  }

  @Override
  protected void receive(DatagramPacket datagramPacket) throws IOException {}

  @Override
  protected void setTTL(byte b) throws IOException {}

  @Override
  protected byte getTTL() throws IOException {
    return 0;
  }

  @Override
  protected void setTimeToLive(int i) throws IOException {}

  @Override
  protected int getTimeToLive() throws IOException {
    return 0;
  }

  @Override
  protected void join(InetAddress inetAddress) throws IOException {}

  @Override
  protected void leave(InetAddress inetAddress) throws IOException {}

  @Override
  protected void joinGroup(SocketAddress socketAddress, NetworkInterface networkInterface)
      throws IOException {}

  @Override
  protected void leaveGroup(SocketAddress socketAddress, NetworkInterface networkInterface)
      throws IOException {}

  @Override
  protected void close() {}

  @Override
  public void setOption(int i, Object o) throws SocketException {}

  @Override
  public Object getOption(int i) throws SocketException {
    return null;
  }
}