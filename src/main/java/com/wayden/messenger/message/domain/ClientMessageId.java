package com.wayden.messenger.message.domain;

import java.util.Objects;
import java.util.UUID;

public record ClientMessageId(UUID value) {
  public ClientMessageId {
    Objects.requireNonNull(value, "Client message ID must not be null");
  }
}
