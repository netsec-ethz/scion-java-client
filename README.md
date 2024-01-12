

***Under construction. Do not use.***

***Under construction. Do not use.***

***Under construction. Do not use.***

# SCION Java client

A Java client for [SCION](https://scion.org).

This client can directly connect to SCION **without dispatcher**.

## API

The central classes of the API are:

- `DatagramChannel`: This class works like a `java.nio.channel.DatagramChannel`. It implements 
  `Channel` and `ByteChannel`. Scattering. gathering, multicast and selectors are currently not
  supported.
- `Path`, `RequestPath`, `ResponsePath`: The notion of path is slightly different than in other 
    parts of Scion. A `Path` contains a route to a destination ("raw path") plus the full 
    destination, i.e. IP-address and port.
  - `RequestPath` is a `Path` with meta information (bandwidth, geo info, etc).
  - `ResponsePath` is a `Path` with source IA, IP & port.
- `PathPolicy` is an interface with several example implementations for:
  first path returned by daemon (default), max bandwidth, min latency, min hops, ...
- `ScionService`: Provides methods to request paths and get ISD/AS information.
  `ScionService` instances can be created with the `Scion` class. The first instance that is created will subsequently
  returned by `Scion.defaultService()`.
- `Scion`, `ScionUtil`, `ScionConstants`: Utility classes.
- `ScionSocketOptions`: Options for the `DatagramChannel`.
- `SCMP` provides `ScmpType` and `ScmpCode` enums with text messages. It also contains
  `ScmpMessage` (for SCMP errors) and `ScmpEcho`/`ScmpTraceroute` types. These can be used with the
  `DatagramChannel`'s `sendXXXRequest()` and `setXXXListener()` methods.
- **TODO** Currently residing in `test`: `ScionPacketInspector`: A packet inspector and builder.
- **TODO** `DatagramSocket` and `DatagramPacket`: These work similar to the old `java.net.DatagramSocket`.
  This is currently deprecated because it does not work well.

### Features
Supported:
- DatagramChannel support: read(), write(), receive(), send(), bind(), connnect(), ... 
- Path selection policies
- Path expiry/refresh
- Packet validation
- DNS/TXT scion entry lookup
- Configurable:
  - daemon address
  - bootstrapping via topo file, bootstrapper IP or DNS 
  - path expiry
- Packet inspector for debugging
- No "dispatcher"

Missing:
- DatagramChannel support for Selectors
- DatagramSockets
- Path construction with short-cuts, on-path, peering
- EPIC
- RHINE
- ...

## Getting started

A simple client looks like this:
```java
InetSocketAddress addr = new InetSocketAddress(...);
try (DatagramChannel channel = DatagramChannel.open()) {
  channel.configureBlocking(true);
  channel.connect(addr);
  channel.write(ByteBuffer.wrap("Hello Scion".getBytes()));
  ...
  ByteBuffer response = ByteBuffer.allocate(1000);
  channel.read(response); 
}
```

## DatagramChannel

### Options

Options are defined in `ScionSocketOptions`, see javadoc for details.

| Option                        | Default | Short description                                               |
|-------------------------------|---------|-----------------------------------------------------------------|
| `SN_API_WRITE_TO_USER_BUFFER`    | `false` | Throw exception when receiving an invalid packet          | 
| `SN_PATH_EXPIRY_MARGIN` | `2`     | A new path is requested if `now + margin > pathExpirationDate` | 

## Performance pitfalls

- **Using `SocketAddress` for `send()`**. `send(buffer, socketAddress)` is a convenience function. However, when sending 
  multiple packets to the same destination, one should use `path = send(buffer, path)` or `connect()` + `write()` in 
  order to avoid frequent path lookups.

- **Using expired path (client).** When using `send(buffer, path)` with an expired `RequestPath`, the channel will 
  transparently look up a new path. This works but causes a path lookup for every `send()`.
  Solution: always use the latest path returned by send, e.g. `path = send(buffer, path)`.

- **Using expired path (server).** When using `send(buffer, path)` with an expired `ResponsePath`, the channel will
  simple send it anyway (could just drop it) TODO
  -> Callback?
  - TODO request new path a few seconds in advance on client side?    



## Demo application - ping pong

There is a simple ping pong client-server application in `src/test/demo`.

It has some hardcoded ports/IP so it works only with the scionlab tiny.topo and only with the dispatcher-free
version of scionlab: https://github.com/scionproto/scion/pull/4344

The client and server connects directly to the border router (without dispatcher).

The server is located in `1-ff00:0:112` (IP [::1]:44444). The client is located in `1-ff00:0:110`.


## Configuration

### Bootstrapping / daemon
In order to find paths and connect to the local AS, the application needs either a (local) 
installation of the Scion Daemon (see here) or some other means to
get bootstrap information.

The method `Scion.defaultService()` (internally called by `DatagramChannel.open()`) will 
attempt to get network information in the following order until it succeeds:
- Check for to daemon
- Check for local topology file (if file name is given)
- Check for bootstrap server address (if address is given)
- Check for DNS NAPTR record (if record entry name is given)

| Option                              | Java property                     | Environment variable         | Default value |
|-------------------------------------|-----------------------------------|------------------------------|---------------|
| Daemon host                         | `org.scion.daemon.host`           | `SCION_DAEMON_HOST`          | localhost     |
| Daemon port                         | `org.scion.daemon.port`           | `SCION_DAEMON_PORT`          | 30255         | 
| Bootstrap topology file path        | `org.scion.bootstrap.topoFile`    | `SCION_BOOTSTRAP_TOPO_FILE`  |               | 
| Bootstrap server host               | `org.scion.bootstrap.host`        | `SCION_BOOTSTRAP_HOST`       |               |
| Bootstrap DNS NAPTR entry host name | `org.scion.bootstrap.naptr.name ` | `SCION_BOOTSTRAP_NAPTR_NAME` |               | 

### Other

| Option                                                                                                                 | Java property           | Environment variable | Default value |
|------------------------------------------------------------------------------------------------------------------------|-------------------------|----------------------|---------------|
| Path expiry margin. Before sending a packet a new path is requested if the path is about to expire with X seconds. | `org.scion.pathExpiryMargin` | `SCION_PATH_EXPIRY_MARGIN`  | 2             |

## FAQ / trouble shooting

### Cannot find symbol javax.annotation.Generated

```
Compilation failure: Compilation failure: 
[ERROR] ...<...>ServiceGrpc.java:[7,18] cannot find symbol
[ERROR]   symbol:   class Generated
[ERROR]   location: package javax.annotation
```

This can be fixed by building with Java JDK 1.8.


