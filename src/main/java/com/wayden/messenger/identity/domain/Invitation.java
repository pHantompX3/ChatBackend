package com.wayden.messenger.identity.domain;

import java.time.Clock;
import java.time.Instant;

public record Invitation(
    InvitationId id,
    InvitationTokenHash tokenHash,
    UserId createdBy,
    Instant expiresAt,
    Instant redeemedAt,
    UserId redeemedBy,
    Instant revokedAt,
    Instant createdAt) {

  public Invitation {
    if (id == null) {
      throw new IllegalArgumentException("Invitation id must not be null");
    }
    if (tokenHash == null) {
      throw new IllegalArgumentException("Invitation token hash must not be null");
    }
    if (createdBy == null) {
      throw new IllegalArgumentException("Created-by user must not be null");
    }
    if (expiresAt == null || createdAt == null) {
      throw new IllegalArgumentException("Expiration and created timestamps must not be null");
    }
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("Expiration must be after creation time");
    }
    boolean redeemColumnsInSync =
        (redeemedAt == null && redeemedBy == null) || (redeemedAt != null && redeemedBy != null);
    if (!redeemColumnsInSync) {
      throw new IllegalArgumentException("Redeemed timestamp and user must be set together");
    }
    if (redeemedAt != null && revokedAt != null) {
      throw new IllegalArgumentException("Invitation cannot be redeemed and revoked");
    }
  }

  public boolean isExpired(Clock clock) {
    return expiresAt.isBefore(clock.instant());
  }

  public boolean isRedeemed() {
    return redeemedAt != null;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }
}
