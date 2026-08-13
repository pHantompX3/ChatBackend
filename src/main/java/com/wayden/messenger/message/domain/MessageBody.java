package com.wayden.messenger.message.domain;

public record MessageBody(String value) {

  public static final int MAX_LENGTH = 4000;

  public MessageBody {
    if (value == null) {
      throw new IllegalArgumentException("Message body must not be null");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("Message body must contain non-whitespace text");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Message body must not exceed " + MAX_LENGTH + " UTF-16 code units");
    }
  }
}
