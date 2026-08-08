package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.UserId;
import java.util.Objects;

public record RevokeInvitationCommand(InvitationId invitationId, UserId actorUserId) {

  public RevokeInvitationCommand {
    Objects.requireNonNull(invitationId, "invitationId must not be null");
    Objects.requireNonNull(actorUserId, "actorUserId must not be null");
  }
}
