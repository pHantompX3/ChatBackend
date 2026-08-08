package com.wayden.messenger.identity.domain;

import java.util.UUID;

public record InvitationId(UUID value) {

  public InvitationId {
    if (value == null) {
      throw new IllegalArgumentException("InvitationId value must not be null");
    }
  }

  public static InvitationId newId() {
    return new InvitationId(UUID.randomUUID());
  }
}
