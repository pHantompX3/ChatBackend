package com.wayden.messenger.session.domain;

import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.Arrays;

public record Session(
    SessionId id,
    UserId userId,
    byte[] tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant lastSeenAt,
    Instant revokedAt,
    String userAgent,
    String sourceAddress,
    SessionStatus status) {

  public Session {
    if (id == null) {
      throw new IllegalArgumentException("Session id must not be null");
    }
    if (userId == null) {
      throw new IllegalArgumentException("Session user id must not be null");
    }
    if (tokenHash == null || tokenHash.length == 0) {
      throw new IllegalArgumentException("Session token hash must not be empty");
    }
    if (createdAt == null || expiresAt == null) {
      throw new IllegalArgumentException("Session timestamps must not be null");
    }
    if (expiresAt.isBefore(createdAt) || expiresAt.equals(createdAt)) {
      throw new IllegalArgumentException("Session expiry must be after creation time");
    }
    if (status == null) {
      throw new IllegalArgumentException("Session status must not be null");
    }
    tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
  }

  @Override
  public byte[] tokenHash() {
    return Arrays.copyOf(tokenHash, tokenHash.length);
  }
}
