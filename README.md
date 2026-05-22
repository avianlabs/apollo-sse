# apollo-sse

[![Maven Central](https://img.shields.io/maven-central/v/net.avianlabs.apollo/apollo-sse.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/net.avianlabs.apollo/apollo-sse)

A Server-Sent Events (`text/event-stream`) [`NetworkTransport`][nt] for [Apollo Kotlin][apollo-kotlin],
enabling GraphQL subscriptions over the [`graphql-sse`][graphql-sse] protocol.

Kotlin Multiplatform: JVM, Android, iosArm64, iosSimulatorArm64, iosX64, macosArm64.

## Installation

```kotlin
// build.gradle.kts
dependencies {
  implementation("net.avianlabs.apollo:apollo-sse:0.1.0")
}
```

## Usage

```kotlin
import com.apollographql.apollo.ApolloClient
import net.avianlabs.apollo.sse.SseNetworkTransport

val sseTransport = SseNetworkTransport.Builder()
  .serverUrl("https://example.com/graphql")
  .addHeader("Authorization", "Bearer $token")
  .reconnectWhen { error, attempt ->
    // Return true to retry, false to surface the error to the subscription
    attempt < 5
  }
  .build()

val apolloClient = ApolloClient.Builder()
  .httpServerUrl("https://example.com/graphql")
  .subscriptionNetworkTransport(sseTransport)
  .build()
```

### Streaming on JVM / Android

If you bring your own `OkHttpClient` via the `okHttpClient(...)` extension, configure
it for streaming — most importantly, disable the read timeout:

```kotlin
val client = OkHttpClient.Builder()
  .connectTimeout(30, TimeUnit.SECONDS)
  .readTimeout(0, TimeUnit.MILLISECONDS)  // no read timeout for long-lived SSE streams
  .build()

val transport = SseNetworkTransport.Builder()
  .serverUrl("https://example.com/graphql")
  .okHttpClient(client)
  .build()
```

If you don't supply an engine, the platform default is used (OkHttp on JVM/Android,
NSURLSession on Apple targets — whatever [`DefaultHttpEngine`][default-engine] resolves
to on your target).

## Compatibility

| apollo-sse | Apollo Kotlin | Kotlin | JVM target |
|------------|---------------|--------|------------|
| 0.1.x      | 5.x           | 2.3.x  | 21         |

## License

Apache 2.0. See [LICENSE](LICENSE).

[nt]: https://www.apollographql.com/docs/kotlin/v5/advanced/network-transport
[apollo-kotlin]: https://github.com/apollographql/apollo-kotlin
[graphql-sse]: https://github.com/enisdenjo/graphql-sse/blob/master/PROTOCOL.md
[default-engine]: https://www.apollographql.com/docs/kotlin/v5/advanced/network-transport#defaulthttpengine
