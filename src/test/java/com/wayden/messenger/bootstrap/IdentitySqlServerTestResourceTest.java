package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdentitySqlServerTestResourceTest {

  @Test
  void retryShouldSucceedAfterTransientFailure() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        assertDoesNotThrow(
            () ->
                IdentitySqlServerTestResource.retry(
                    "bootstrap",
                    () -> {
                      if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("transient");
                      }
                      return "ok";
                    },
                    3,
                    Duration.ofMillis(1)));

    assertEquals("ok", result);
    assertEquals(3, attempts.get());
  }

  @Test
  void retryShouldFailAfterExhaustingAttempts() {
    AtomicInteger attempts = new AtomicInteger();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                IdentitySqlServerTestResource.retry(
                    "bootstrap",
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("persistent");
                    },
                    2,
                    Duration.ZERO));

    assertEquals("persistent", exception.getMessage());
    assertEquals(2, attempts.get());
  }
}
