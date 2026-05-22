package net.avianlabs.apollo.sse.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.avianlabs.apollo.sse.SseEvent
import okio.Buffer

class SseReaderTest {

  private fun sseReader(text: String): SseReader {
    val buffer = Buffer().writeUtf8(text)
    return SseReader(buffer)
  }

  @Test
  fun emptySource_emitsNothing() = runTest {
    val events = sseReader("").readEvents().toList()
    assertTrue(events.isEmpty())
  }

  @Test
  fun singleNextEvent_parsesCorrectly() = runTest {
    val input = "event: next\ndata: {\"data\":{\"price\":\"1.23\"}}\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals(SseEvent(event = "next", data = "{\"data\":{\"price\":\"1.23\"}}"), events[0])
  }

  @Test
  fun completeEvent_parsesCorrectly() = runTest {
    val input = "event: complete\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals(SseEvent(event = "complete", data = null), events[0])
  }

  @Test
  fun commentLine_isSkipped() = runTest {
    val input = ": ping\n\nevent: next\ndata: {\"data\":{\"x\":1}}\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals("next", events[0].event)
  }

  @Test
  fun multilineData_concatenatedWithNewline() = runTest {
    val input = "event: next\ndata: {\"data\":\ndata: {\"price\":\"1.23\"}}\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals("{\"data\":\n{\"price\":\"1.23\"}}", events[0].data)
  }

  @Test
  fun multipleEvents_allEmitted() = runTest {
    val input = "event: next\ndata: {\"data\":{\"price\":\"1.00\"}}\n\n: ping\n\nevent: next\ndata: {\"data\":{\"price\":\"2.00\"}}\n\nevent: complete\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(3, events.size)
    assertEquals("next", events[0].event)
    assertEquals("{\"data\":{\"price\":\"1.00\"}}", events[0].data)
    assertEquals("next", events[1].event)
    assertEquals("{\"data\":{\"price\":\"2.00\"}}", events[1].data)
    assertEquals("complete", events[2].event)
  }

  @Test
  fun dataWithoutEvent_usesNullEventType() = runTest {
    val input = "data: some data\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals(SseEvent(event = null, data = "some data"), events[0])
  }

  @Test
  fun fieldsWithSpaceAfterColon_trimmed() = runTest {
    val input = "event:  next\ndata:  {\"data\":{}}\n\n"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
    assertEquals("next", events[0].event)
    assertEquals("{\"data\":{}}", events[0].data)
  }

  @Test
  fun incompleteBlock_emittedOnStreamEnd() = runTest {
    val input = "event: next\ndata: {\"data\":{}}"
    val events = sseReader(input).readEvents().toList()
    assertEquals(1, events.size)
  }
}
