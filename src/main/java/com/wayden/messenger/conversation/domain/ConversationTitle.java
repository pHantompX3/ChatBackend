package com.wayden.messenger.conversation.domain;

public record ConversationTitle(String value) {
  public ConversationTitle {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Group title must not be blank");
    }
    value = value.trim();
    if (value.length() > 200) {
      throw new IllegalArgumentException("Group title must not exceed 200 characters");
    }
  }
}
