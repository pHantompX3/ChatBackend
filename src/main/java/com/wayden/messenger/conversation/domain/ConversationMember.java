package com.wayden.messenger.conversation.domain;

import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;

public record ConversationMember(
    ConversationId conversationId,
    UserId userId,
    String username,
    ConversationRole role,
    Instant joinedAt,
    Instant leftAt,
    long lastDeliveredSequence,
    long lastReadSequence) {

  public ConversationMember {
    if (conversationId == null || userId == null || role == null || joinedAt == null) {
      throw new IllegalArgumentException("Conversation membership fields must not be null");
    }
    if (username != null && username.isBlank()) {
      throw new IllegalArgumentException("Member username must not be blank");
    }
    if (leftAt != null && leftAt.isBefore(joinedAt)) {
      throw new IllegalArgumentException("Member departure cannot precede joining");
    }
    if (lastDeliveredSequence < 0
        || lastReadSequence < 0
        || lastReadSequence > lastDeliveredSequence) {
      throw new IllegalArgumentException("Member message positions are invalid");
    }
  }

  public boolean isActive() {
    return leftAt == null;
  }
}
