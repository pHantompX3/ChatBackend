package com.wayden.messenger.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NormalizedUsernameTest {

  @Test
  void shouldNormalizeUsernameAsLowercaseAndTrimmed() {
    NormalizedUsername normalized = NormalizedUsername.fromRaw("  Alice.Admin  ");

    assertEquals("alice.admin", normalized.value());
  }

  @Test
  void shouldRejectBlankUsernames() {
    assertThrows(IllegalArgumentException.class, () -> NormalizedUsername.fromRaw("   "));
  }
}
