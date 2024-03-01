# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Code coverage. [#11](https://github.com/tzaeschke/phtree-cpp/pull/11)
  
### Changed
- BREAKING CHANGE: Changed maven artifactId to "client"
  [#9](https://github.com/tzaeschke/phtree-cpp/pull/9)
- BREAKING CHANGE: SCMP refactoring, renamed several SCMP related classes.
  [#14](https://github.com/tzaeschke/phtree-cpp/pull/14), 
  [#15](https://github.com/tzaeschke/phtree-cpp/pull/15),
  [#17](https://github.com/tzaeschke/phtree-cpp/pull/17),
  [#19](https://github.com/tzaeschke/phtree-cpp/pull/19),
  [#20](https://github.com/tzaeschke/phtree-cpp/pull/21)
- BREAKING CHANGE:`ScionService` instances created via `Scion.newXYZ`
  will not be considered by `Scion.defaultService()`. Also, `DatagramChannel.getService()`
  does not create a service if none exists.
  [#18](https://github.com/tzaeschke/phtree-cpp/pull/18)
- Doc cleanup
  [#22](https://github.com/tzaeschke/phtree-cpp/pull/22)


### Fixed
- Fixed: SCMP problem when pinging local AS.
  [#16](https://github.com/tzaeschke/phtree-cpp/pull/16),
- Fixed SCMP timeout and error handling (IOExceptions + SCMP errors).
  [#13](https://github.com/tzaeschke/phtree-cpp/pull/13)
- CI (only) failures on JDK 8. [#10](https://github.com/tzaeschke/phtree-cpp/pull/10)
- Sporadic CI (only) failures. [#12](https://github.com/tzaeschke/phtree-cpp/pull/12)

### Removed

- Removed all code related to DatagramSockets
  [#21](https://github.com/tzaeschke/phtree-cpp/pull/21)


## [0.1.0-ALPHA] - 2024-02-01

### Added
- `DatagramSocket` [#7](https://github.com/tzaeschke/phtree-cpp/pull/7)
- `Path`, `RequestPath`, `ResponsePath` [#7](https://github.com/tzaeschke/phtree-cpp/pull/7)
- `Scion`, `ScionService` [#7](https://github.com/tzaeschke/phtree-cpp/pull/7)
- `ScmpChannel` for SCMP [#7](https://github.com/tzaeschke/phtree-cpp/pull/7)

### Changed
- Nothing

### Fixed
- Nothing

### Removed
- Nothing

[Unreleased]: https://github.com/netsec-ethz/scion-java-client/compare/v0.1.0-ALPHA...HEAD
[0.1.0-ALPHA]: https://github.com/netsec-ethz/scion-java-client/compare/init_root_commit...v0.1.0-ALPHA
