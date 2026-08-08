package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.UserId;

public interface IdGenerator {
  UserId newUserId();

  InvitationId newInvitationId();
}
