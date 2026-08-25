package com.wayden.messenger.realtime.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record RealtimeEventEnvelope(
    @JsonProperty("eventId") UUID eventId,
    @JsonProperty("eventType") String eventType,
    @JsonProperty("occurredAt") Instant occurredAt,
    @JsonProperty("conversationId") UUID conversationId,
    @JsonProperty("payload") Object payload) {}
