package com.wayden.messenger.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Argon2PasswordHasherTest {

  @Test
  void shouldHashAndVerifyPassword() {
    Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    var hash = hasher.hash("Strong-Passw0rd!");

    assertTrue(hasher.verify("Strong-Passw0rd!", hash));
    assertFalse(hasher.verify("wrong-pass", hash));
  }
}
