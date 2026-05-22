package net.avianlabs.apollo.sse

import com.apollographql.apollo.network.http.DefaultHttpEngine
import okhttp3.OkHttpClient

/**
 * Configures the SSE transport to use the supplied [OkHttpClient] as its underlying HTTP engine.
 *
 * Available on JVM and Android targets only.
 *
 * Tip: for long-lived SSE streams, configure your `OkHttpClient` with
 * `readTimeout(0, TimeUnit.MILLISECONDS)` so reads never time out:
 *
 * ```
 * val client = OkHttpClient.Builder()
 *   .readTimeout(0, TimeUnit.MILLISECONDS)
 *   .build()
 *
 * SseNetworkTransport.Builder()
 *   .serverUrl("https://example.com/graphql")
 *   .okHttpClient(client)
 *   .build()
 * ```
 */
public fun SseNetworkTransport.Builder.okHttpClient(
  client: OkHttpClient,
): SseNetworkTransport.Builder = httpEngine(DefaultHttpEngine { client })
