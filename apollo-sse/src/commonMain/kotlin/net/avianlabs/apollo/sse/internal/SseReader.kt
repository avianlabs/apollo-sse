package net.avianlabs.apollo.sse.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.avianlabs.apollo.sse.SseEvent
import okio.BufferedSource

/**
 * Reads Server-Sent Events from a streaming [BufferedSource] per the SSE specification.
 *
 * Parsing rules:
 * - Lines starting with `:` are comments (ignored)
 * - `event:<value>` sets the event type for the current block
 * - `data:<value>` appends to the data buffer (multi-line data joined with newlines)
 * - An empty line dispatches the accumulated event and resets the accumulators
 * - End of stream dispatches any remaining accumulated data
 */
internal class SseReader(private val source: BufferedSource) {

  fun readEvents(): Flow<SseEvent> = flow {
    var event: String? = null
    var data: StringBuilder? = null

    while (!source.exhausted()) {
      val line = source.readUtf8Line() ?: break

      when {
        line.isEmpty() -> {
          if (event != null || data != null) {
            emit(SseEvent(event = event, data = data?.toString()))
            event = null
            data = null
          }
        }
        line.startsWith(":") -> {
          // Comment line — used for keep-alive pings. Ignore.
        }
        line.startsWith("event:") -> {
          event = line.removePrefix("event:").trimStart()
        }
        line.startsWith("data:") -> {
          val value = line.removePrefix("data:").trimStart()
          if (data == null) {
            data = StringBuilder(value)
          } else {
            data.append('\n').append(value)
          }
        }
        // Unknown fields are ignored per the SSE spec
      }
    }

    // Emit any remaining accumulated event when the stream ends
    if (event != null || data != null) {
      emit(SseEvent(event = event, data = data?.toString()))
    }
  }
}
