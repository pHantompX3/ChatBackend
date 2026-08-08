package com.wayden.messenger.identity.domain;

public record PasswordHash(String value) {

  public PasswordHash {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Password hash must not be blank");
    }
  }
}
