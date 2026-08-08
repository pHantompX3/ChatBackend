package com.wayden.messenger.identity.application;

import java.util.Objects;

public record BootstrapAdminCommand(String username, String password) {

  public BootstrapAdminCommand {
    Objects.requireNonNull(username, "username must not be null");
    Objects.requireNonNull(password, "password must not be null");
  }
}
