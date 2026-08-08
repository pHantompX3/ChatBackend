package com.wayden.messenger.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class Sha256InvitationTokenHasherTest {

  @Test
  void shouldProduceStableHashForSameToken() {
    Sha256InvitationTokenHasher hasher = new Sha256InvitationTokenHasher();

    byte[] first = hasher.hash("invite-token").value();
    byte[] second = hasher.hash("invite-token").value();

    assertEquals(32, first.length);
    assertArrayEquals(first, second);
  }
}
