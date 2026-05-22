package net.avianlabs.apollo.sse

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.exception.ApolloHttpException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class SseNetworkTransportTest {

  private lateinit var server: MockWebServer
  private lateinit var transport: SseNetworkTransport

  @BeforeTest
  fun setUp() {
    server = MockWebServer()
    server.start()
    transport = SseNetworkTransport.Builder()
      .serverUrl(server.url("/graphql").toString())
      .build()
  }

  @AfterTest
  fun tearDown() {
    transport.dispose()
    server.shutdown()
  }

  @Test
  fun sendsCorrectHeaders() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: complete\n\n")
    )

    transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()

    val req = server.takeRequest()
    assertEquals("POST", req.method)
    assertEquals("text/event-stream", req.getHeader("Accept"))
    assertTrue(req.getHeader("Content-Type")!!.contains("application/json"))
  }

  @Test
  fun parsesNextEvents() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody(
          "event: next\ndata: {\"data\":{\"test\":\"v1\"}}\n\nevent: next\ndata: {\"data\":{\"test\":\"v2\"}}\n\nevent: complete\n\n"
        )
    )

    val responses = transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()
    assertEquals(2, responses.size)
    assertNull(responses[0].exception)
    assertNull(responses[1].exception)
  }

  @Test
  fun completeEvent_terminatesFlow() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody(
          "event: next\ndata: {\"data\":{\"test\":\"v\"}}\n\nevent: complete\n\nevent: next\ndata: {\"data\":{\"test\":\"skip\"}}\n\n"
        )
    )

    val responses = transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()
    assertEquals(1, responses.size)
  }

  @Test
  fun nonOkStatus_emitsErrorResponse() = runTest {
    server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

    val responses = transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()
    assertEquals(1, responses.size)
    assertNotNull(responses[0].exception)
    assertTrue(responses[0].exception is ApolloHttpException)
  }

  @Test
  fun customHeaders_areSent() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: complete\n\n")
    )

    transport.dispose()
    transport = SseNetworkTransport.Builder()
      .serverUrl(server.url("/graphql").toString())
      .addHeader("Authorization", "Bearer test-token")
      .addHeader("X-Custom", "custom-value")
      .build()

    transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()

    val req = server.takeRequest()
    assertEquals("Bearer test-token", req.getHeader("Authorization"))
    assertEquals("custom-value", req.getHeader("X-Custom"))
  }

  @Test
  fun reconnectWhen_retriesOnError() = runTest {
    // First request: send one complete event, then disconnect mid-stream (simulates a broken
    // connection). The body is padded so the disconnect happens after the first event is fully
    // written to the socket but before the stream ends cleanly.
    val padding = "x".repeat(200)
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: next\ndata: {\"data\":{\"test\":\"v1\"}}\n\nevent: next\ndata: $padding")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
    )

    // Second request: send one event then complete normally
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody("event: next\ndata: {\"data\":{\"test\":\"v2\"}}\n\nevent: complete\n\n")
    )

    transport.dispose()
    transport = SseNetworkTransport.Builder()
      .serverUrl(server.url("/graphql").toString())
      .reconnectWhen { _, _ -> true }
      .build()

    val responses = transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()

    assertEquals(2, responses.size)
    assertNull(responses[0].exception)
    assertNull(responses[1].exception)
    assertEquals(2, server.requestCount)
  }

  @Test
  fun reconnectWhen_stopsWhenReturnsFalse() = runTest {
    server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

    transport.dispose()
    transport = SseNetworkTransport.Builder()
      .serverUrl(server.url("/graphql").toString())
      .reconnectWhen { _, _ -> false }
      .build()

    val responses = transport.execute(ApolloRequest.Builder(TestSubscription()).build()).toList()

    assertEquals(1, responses.size)
    assertNotNull(responses[0].exception)
    assertTrue(responses[0].exception is ApolloHttpException)
    assertEquals(1, server.requestCount)
  }
}
