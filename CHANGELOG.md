# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - TBD

### Added

- Initial release. Server-Sent Events `NetworkTransport` for Apollo Kotlin 5.x,
  with Kotlin Multiplatform support for JVM, Android, iosArm64, iosSimulatorArm64,
  iosX64, and macosArm64 targets.
- `SseNetworkTransport` and `Builder` API for configuring server URL, headers,
  custom `HttpEngine`, and reconnection policy.
- `okHttpClient(OkHttpClient)` builder extension for JVM and Android consumers.
