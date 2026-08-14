package com.wayden.messenger.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaginationPolicyTest {

  @Test
  void limitMustStayWithinTheDeclaredContract() {
    assertEquals(20, PaginationPolicy.resolveLimit(null, 20, 50));
    assertEquals(1, PaginationPolicy.resolveLimit(1, 20, 50));
    assertEquals(50, PaginationPolicy.resolveLimit(50, 20, 50));
    assertThrows(IllegalArgumentException.class, () -> PaginationPolicy.resolveLimit(0, 20, 50));
    assertThrows(IllegalArgumentException.class, () -> PaginationPolicy.resolveLimit(51, 20, 50));
  }

  @Test
  void oversizedCursorMustBeRejectedBeforeDecoding() {
    String oversized = "x".repeat(PaginationPolicy.MAX_CURSOR_LENGTH + 1);
    assertThrows(
        IllegalArgumentException.class, () -> PaginationPolicy.requireValidCursorLength(oversized));
  }
}
