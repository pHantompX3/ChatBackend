package com.wayden.messenger.conversation.domain;

import java.util.UUID;

public record ConversationId(UUID value) {
  public ConversationId {
    if (value == null) {
      throw new IllegalArgumentException("Conversation id must not be null");
    }
  }

  public static ConversationId newId() {
    return new ConversationId(UUID.randomUUID());
  }
}
