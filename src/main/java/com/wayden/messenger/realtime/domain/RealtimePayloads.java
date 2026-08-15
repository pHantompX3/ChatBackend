package com.wayden.messenger.realtime.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public final class RealtimePayloads {

  private RealtimePayloads() {}

  public record MessageCreatedPayload(
      @JsonProperty("conversationId") UUID conversationId,
      @JsonProperty("messageId") UUID messageId,
      @JsonProperty("sequenceNumber") long sequenceNumber,
      @JsonProperty("senderId") UUID senderId,
      @JsonProperty("clientMessageId") UUID clientMessageId,
      @JsonProperty("body") String body,
      @JsonProperty("createdAt") Instant createdAt) {}

  public record MessageEditedPayload(
      @JsonProperty("conversationId") UUID conversationId,
      @JsonProperty("messageId") UUID messageId,
      @JsonProperty("sequenceNumber") long sequenceNumber,
      @JsonProperty("body") String body,
      @JsonProperty("editedAt") Instant editedAt) {}

  public record MessageDeletedPayload(
      @JsonProperty("conversationId") UUID conversationId,
      @JsonProperty("messageId") UUID messageId,
      @JsonProperty("sequenceNumber") long sequenceNumber,
      @JsonProperty("deletedAt") Instant deletedAt) {}

  public record DeliveryUpdatedPayload(
      @JsonProperty("conversationId") UUID conversationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("lastDeliveredSequence") long lastDeliveredSequence,
      @JsonProperty("updatedAt") Instant updatedAt) {}

  public record ReadUpdatedPayload(
      @JsonProperty("conversationId") UUID conversationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("lastReadSequence") long lastReadSequence,
      @JsonProperty("lastDeliveredSequence") long lastDeliveredSequence,
      @JsonProperty("updatedAt") Instant updatedAt) {}
}
