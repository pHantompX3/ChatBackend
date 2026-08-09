package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.IdGenerator;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UuidIdGenerator implements IdGenerator {

  @Override
  public UserId newUserId() {
    return UserId.newId();
  }

  @Override
  public InvitationId newInvitationId() {
    return InvitationId.newId();
  }
}
