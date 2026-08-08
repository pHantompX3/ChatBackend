package com.wayden.messenger.identity.domain;

import java.time.Instant;

public record User(
    UserId id,
    String username,
    NormalizedUsername normalizedUsername,
    PasswordHash passwordHash,
    SystemRole systemRole,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public User {
    if (id == null) {
      throw new IllegalArgumentException("User id must not be null");
    }
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username must not be blank");
    }
    if (normalizedUsername == null) {
      throw new IllegalArgumentException("Normalized username must not be null");
    }
    if (passwordHash == null) {
      throw new IllegalArgumentException("Password hash must not be null");
    }
    if (systemRole == null) {
      throw new IllegalArgumentException("System role must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("User status must not be null");
    }
    if (createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("Timestamps must not be null");
    }
  }
}
