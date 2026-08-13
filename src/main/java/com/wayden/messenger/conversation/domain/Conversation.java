package com.wayden.messenger.conversation.domain;

import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;

public record Conversation(
    ConversationId id,
    ConversationType type,
    ConversationTitle title,
    UserId createdBy,
    long nextMessageSequence,
    Instant createdAt,
    Instant updatedAt) {

  public Conversation {
    if (id == null || type == null || createdBy == null) {
      throw new IllegalArgumentException("Conversation identity must not be null");
    }
    if ((type == ConversationType.DIRECT && title != null)
        || (type == ConversationType.GROUP && title == null)) {
      throw new IllegalArgumentException("Conversation title does not match its type");
    }
    if (nextMessageSequence <= 0) {
      throw new IllegalArgumentException("Next message sequence must be positive");
    }
    if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("Conversation timestamps are invalid");
    }
  }
}
