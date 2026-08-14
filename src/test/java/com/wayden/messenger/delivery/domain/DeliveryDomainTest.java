package com.wayden.messenger.delivery.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.delivery.domain.AcknowledgementResult.Outcome;
import com.wayden.messenger.message.domain.MessageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DeliveryDomainTest {

  @Test
  void positionShouldEnforceCursorAndHighWaterInvariants() {
    assertDoesNotThrow(() -> new MessagePosition(0, 0, 0, 0));
    assertDoesNotThrow(() -> new MessagePosition(10, 8, 5, 3));
    assertThrows(IllegalArgumentException.class, () -> new MessagePosition(-1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new MessagePosition(3, 4, 2, 0));
    assertThrows(IllegalArgumentException.class, () -> new MessagePosition(3, 2, 3, 0));
  }

  @Test
  void acknowledgementShouldMatchItsMonotonicOutcome() {
    assertDoesNotThrow(() -> new AcknowledgementResult(5, 1, 4, 1, 1, Outcome.ADVANCED));
    assertDoesNotThrow(() -> new AcknowledgementResult(5, 4, 4, 2, 2, Outcome.UNCHANGED));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AcknowledgementResult(5, 4, 3, 2, 2, Outcome.ADVANCED));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AcknowledgementResult(5, 4, 4, 2, 2, Outcome.ADVANCED));
  }

  @Test
  void statusShouldEnforceCountsAndAggregateFlags() {
    MessageId messageId = new MessageId(UUID.randomUUID());
    assertDoesNotThrow(() -> new MessageDeliveryStatus(messageId, 1, true, 0, 0, 0, false, false));
    assertDoesNotThrow(() -> new MessageDeliveryStatus(messageId, 1, true, 2, 2, 1, true, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MessageDeliveryStatus(messageId, 1, true, 1, 2, 0, false, false));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MessageDeliveryStatus(messageId, 1, true, 0, 0, 0, true, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MessageDeliveryStatus(messageId, 1, false, 1, 0, 0, false, false));
  }
}
