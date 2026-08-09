package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.Objects;

public record CreateInvitationCommand(UserId actorUserId, Instant expiresAt) {

  public CreateInvitationCommand {
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }
}
