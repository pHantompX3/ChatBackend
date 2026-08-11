package com.wayden.messenger.session.domain;

import java.util.UUID;

public record SessionId(UUID value) {
  public SessionId {
    if (value == null) {
      throw new IllegalArgumentException("Session id must not be null");
    }
  }
}
