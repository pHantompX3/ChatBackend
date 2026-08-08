package com.wayden.messenger.identity.domain;

import java.util.Locale;

public record NormalizedUsername(String value) {

  public NormalizedUsername {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Normalized username must not be blank");
    }
    if (!value.equals(value.trim().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Normalized username must be lowercase and trimmed");
    }
  }

  public static NormalizedUsername fromRaw(String rawUsername) {
    if (rawUsername == null) {
      throw new IllegalArgumentException("Username must not be null");
    }
    final String normalized = rawUsername.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Username must not be blank");
    }
    return new NormalizedUsername(normalized);
  }
}
