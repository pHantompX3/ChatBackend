package com.wayden.messenger.identity.domain;

import java.util.Arrays;

public record InvitationTokenHash(byte[] value) {

  public InvitationTokenHash {
    if (value == null || value.length == 0) {
      throw new IllegalArgumentException("Invitation token hash must not be empty");
    }
    value = Arrays.copyOf(value, value.length);
  }

  @Override
  public byte[] value() {
    return Arrays.copyOf(value, value.length);
  }
}
