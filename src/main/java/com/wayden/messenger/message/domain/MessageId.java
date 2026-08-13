package com.wayden.messenger.message.domain;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {
  public MessageId {
    Objects.requireNonNull(value, "Message ID must not be null");
  }
}
