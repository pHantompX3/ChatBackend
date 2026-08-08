package com.wayden.messenger.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InvitationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-08T00:00:00Z");

  @Test
  void shouldReportExpiredWhenClockIsPastExpiration() {
    Invitation invitation =
        new Invitation(
            new InvitationId(UUID.randomUUID()),
            new InvitationTokenHash(new byte[] {1, 2, 3}),
            new UserId(UUID.randomUUID()),
            CREATED_AT.plusSeconds(60),
            null,
            null,
            null,
            CREATED_AT);

    Clock afterExpiry = Clock.fixed(CREATED_AT.plusSeconds(120), ZoneOffset.UTC);

    assertTrue(invitation.isExpired(afterExpiry));
    assertFalse(invitation.isRedeemed());
    assertFalse(invitation.isRevoked());
  }

  @Test
  void shouldRejectInconsistentRedeemFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Invitation(
                new InvitationId(UUID.randomUUID()),
                new InvitationTokenHash(new byte[] {1, 2, 3}),
                new UserId(UUID.randomUUID()),
                CREATED_AT.plusSeconds(60),
                CREATED_AT.plusSeconds(30),
                null,
                null,
                CREATED_AT));
  }
}
