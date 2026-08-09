package com.wayden.messenger.identity.application;

import java.util.Objects;

public record RedeemInvitationCommand(String invitationToken, String username, String password) {

  public RedeemInvitationCommand {
    Objects.requireNonNull(invitationToken, "invitationToken must not be null");
    Objects.requireNonNull(username, "username must not be null");
    Objects.requireNonNull(password, "password must not be null");
  }
}
