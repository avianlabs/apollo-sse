package net.avianlabs.apollo.sse

/**
 * Represents a single Server-Sent Event parsed from a `text/event-stream` response.
 *
 * @param event The event type (e.g. "next", "complete"). Null for comment-only events.
 * @param data The event data payload. For "next" events, this is a GraphQL JSON response.
 *             Null for "complete" or comment-only events.
 */
public data class SseEvent(
  val event: String?,
  val data: String?,
)
