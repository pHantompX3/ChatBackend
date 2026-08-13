package com.wayden.messenger.message.api;

import com.wayden.messenger.message.domain.Message;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID messageId,
    UUID conversationId,
    UUID senderId,
    UUID clientMessageId,
    long sequenceNumber,
    String type,
    String body,
    Instant createdAt,
    Instant editedAt,
    Instant deletedAt) {

  public static MessageResponse from(Message message) {
    return new MessageResponse(
        message.id().value(),
        message.conversationId().value(),
        message.senderId().value(),
        message.clientMessageId().value(),
        message.sequenceNumber(),
        message.type().name(),
        message.body() == null ? null : message.body().value(),
        message.createdAt(),
        message.editedAt(),
        message.deletedAt());
  }
}
