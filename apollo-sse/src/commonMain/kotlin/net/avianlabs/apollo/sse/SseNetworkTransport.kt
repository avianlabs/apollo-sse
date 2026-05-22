package net.avianlabs.apollo.sse

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.http.DefaultHttpRequestComposer
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequestComposer
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.toApolloResponse
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import net.avianlabs.apollo.sse.internal.SseReader
import okio.Buffer

@OptIn(ApolloExperimental::class)
public class SseNetworkTransport private constructor(
  private val httpRequestComposer: HttpRequestComposer,
  private val engine: HttpEngine,
  private val headers: List<HttpHeader>,
  private val reconnectWhen: (suspend (Throwable, attempt: Long) -> Boolean)?,
) : NetworkTransport {

  override fun <D : Operation.Data> execute(
    request: ApolloRequest<D>,
  ): Flow<ApolloResponse<D>> {
    val customScalarAdapters = request.executionContext[CustomScalarAdapters] ?: CustomScalarAdapters.Empty

    return if (reconnectWhen != null) {
      flow {
        var attempt = 0L
        var shouldRetry = true
        while (shouldRetry) {
          var hadEvents = false
          try {
            executeSingle(request, customScalarAdapters).collect { response ->
              hadEvents = true
              emit(response)
            }
            shouldRetry = false
          } catch (e: CancellationException) {
            throw e
          } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (hadEvents) attempt = 0
            if (reconnectWhen.invoke(e, attempt)) {
              attempt++
            } else {
              emit(errorResponse(request.operation, e))
              shouldRetry = false
            }
          }
        }
      }
    } else {
      executeSingle(request, customScalarAdapters)
    }
  }

  private fun <D : Operation.Data> executeSingle(
    request: ApolloRequest<D>,
    customScalarAdapters: CustomScalarAdapters,
  ): Flow<ApolloResponse<D>> = flow {
    val composedRequest = httpRequestComposer.compose(request)

    // Replace any Accept header added by the composer (e.g. the subscription multipart value)
    // with the SSE-specific value, then append any user-configured headers.
    val sseHeaders = composedRequest.headers
      .filter { it.name.lowercase() != "accept" } +
      HttpHeader("Accept", "text/event-stream") +
      headers

    val httpRequest = composedRequest.newBuilder().headers(sseHeaders).build()

    val httpResponse = try {
      engine.execute(httpRequest)
    } catch (e: CancellationException) {
      throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
      emit(errorResponse(request.operation, e))
      return@flow
    }

    if (httpResponse.statusCode !in 200..299) {
      runCatching { httpResponse.body?.close() }
      emit(
        errorResponse(
          request.operation,
          ApolloHttpException(
            statusCode = httpResponse.statusCode,
            headers = httpResponse.headers,
            body = null,
            message = "SSE request failed with status code `${httpResponse.statusCode}`",
          )
        )
      )
      return@flow
    }

    val body = httpResponse.body
    if (body == null) {
      emit(
        errorResponse(
          request.operation,
          ApolloNetworkException(
            message = "SSE response has no body",
          )
        )
      )
      return@flow
    }

    val sseReader = SseReader(body)
    sseReader.readEvents()
      .takeWhile { it.event != "complete" }
      .onCompletion { runCatching { body.close() } }
      .collect { sseEvent ->
        if (sseEvent.event == "next") {
          val data = sseEvent.data
          if (data != null) {
            val response = Buffer().writeUtf8(data).jsonReader()
              .toApolloResponse(
                operation = request.operation,
                customScalarAdapters = customScalarAdapters,
                deferredFragmentIdentifiers = null,
              )
            emit(response)
          }
        }
        // Unknown events are silently skipped per SSE spec
      }
  }

  private fun <D : Operation.Data> errorResponse(
    operation: Operation<D>,
    throwable: Throwable,
  ): ApolloResponse<D> {
    val exception = if (throwable is ApolloException) {
      throwable
    } else {
      ApolloNetworkException(message = "SSE error: ${throwable.message}", platformCause = throwable)
    }

    return ApolloResponse.Builder(requestUuid = uuid4(), operation = operation)
      .exception(exception)
      .isLast(true)
      .build()
  }

  override fun dispose() {
    engine.close()
  }

  public class Builder {
    private var serverUrl: String? = null
    private var httpRequestComposer: HttpRequestComposer? = null
    private var engine: HttpEngine? = null
    private val headers = mutableListOf<HttpHeader>()
    private var reconnectWhen: (suspend (Throwable, attempt: Long) -> Boolean)? = null

    public fun serverUrl(url: String): Builder = apply { this.serverUrl = url }

    public fun httpRequestComposer(composer: HttpRequestComposer): Builder = apply {
      this.httpRequestComposer = composer
    }

    public fun httpEngine(engine: HttpEngine): Builder = apply { this.engine = engine }

    public fun addHeader(name: String, value: String): Builder = apply {
      this.headers.add(HttpHeader(name, value))
    }

    public fun reconnectWhen(
      reconnectWhen: suspend (Throwable, attempt: Long) -> Boolean,
    ): Builder = apply { this.reconnectWhen = reconnectWhen }

    public fun build(): SseNetworkTransport {
      val composer = httpRequestComposer ?: DefaultHttpRequestComposer(
        checkNotNull(serverUrl) { "serverUrl or httpRequestComposer must be set" }
      )
      val httpEngine = engine ?: DefaultHttpEngine()
      return SseNetworkTransport(
        httpRequestComposer = composer,
        engine = httpEngine,
        headers = headers.toList(),
        reconnectWhen = reconnectWhen,
      )
    }
  }
}
