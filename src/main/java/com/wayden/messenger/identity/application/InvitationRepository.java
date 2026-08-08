package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.Invitation;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public interface InvitationRepository {
  Invitation save(Invitation invitation);

  Optional<Invitation> findByTokenHash(InvitationTokenHash tokenHash);

  boolean markRevoked(InvitationId invitationId, UserId actorUserId, Instant revokedAt);

  boolean markRedeemed(InvitationId invitationId, UserId actorUserId, Instant redeemedAt);
}
