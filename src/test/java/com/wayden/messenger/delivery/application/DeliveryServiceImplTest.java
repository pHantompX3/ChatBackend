package com.wayden.messenger.delivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryRepository.AcknowledgementAttempt;
import com.wayden.messenger.delivery.domain.AcknowledgementResult;
import com.wayden.messenger.delivery.domain.AcknowledgementResult.Outcome;
import com.wayden.messenger.identity.domain.UserId;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DeliveryServiceImplTest {

  private static final UserId ACTOR_ID = new UserId(UUID.randomUUID());
  private static final ConversationId CONVERSATION_ID = new ConversationId(UUID.randomUUID());
  private static final Object DEADLOCK = new Object();

  @Test
  void acknowledgementShouldRetryTwoDeadlocksAndAuditCommittedResult() {
    var committed =
        new AcknowledgementAttempt.Acknowledged(
            new AcknowledgementResult(4, 0, 3, 0, 0, Outcome.ADVANCED));
    var attempt = new ScriptedAttempt(List.of(deadlock(), deadlock(), committed));
    var audit = new RequestAuditContext();
    var service = new DeliveryServiceImpl(null, attempt, audit);

    service.acknowledgeDelivery(ACTOR_ID, CONVERSATION_ID, 3L);

    assertEquals(3, attempt.calls);
    assertEquals("2", audit.getCustomAttributes().get("deliveryDeadlockRetryCount"));
    assertEquals("ADVANCED", audit.getCustomAttributes().get("deliveryOutcome"));
    assertEquals("3", audit.getCustomAttributes().get("currentDeliveredSequence"));
  }

  @Test
  void acknowledgementShouldExposeNormalTypedOutcomes() {
    var notFound = new ScriptedAttempt(List.of(new AcknowledgementAttempt.ResourceNotFound()));
    var ahead = new ScriptedAttempt(List.of(new AcknowledgementAttempt.SequenceAhead(2)));

    assertThrows(
        DeliveryExceptions.ResourceNotFoundException.class,
        () ->
            new DeliveryServiceImpl(null, notFound, new RequestAuditContext())
                .acknowledgeRead(ACTOR_ID, CONVERSATION_ID, 1L));
    assertThrows(
        DeliveryExceptions.SequenceAheadException.class,
        () ->
            new DeliveryServiceImpl(null, ahead, new RequestAuditContext())
                .acknowledgeRead(ACTOR_ID, CONVERSATION_ID, 3L));
  }

  @Test
  void acknowledgementShouldWrapExhaustedDeadlocks() {
    var attempt = new ScriptedAttempt(List.of(deadlock(), deadlock(), deadlock()));
    var audit = new RequestAuditContext();
    var service = new DeliveryServiceImpl(null, attempt, audit);

    DeliveryExceptions.InternalException failure =
        assertThrows(
            DeliveryExceptions.InternalException.class,
            () -> service.acknowledgeRead(ACTOR_ID, CONVERSATION_ID, 1L));

    assertInstanceOf(DeliveryExceptions.DeadlockException.class, failure.getCause());
    assertEquals("2", audit.getCustomAttributes().get("deliveryDeadlockRetryCount"));
    assertEquals("true", audit.getCustomAttributes().get("deliveryDeadlockRetryExhausted"));
  }

  @Test
  void acknowledgementShouldRejectMissingAndNegativeSequenceBeforeAttempt() {
    var attempt = new ScriptedAttempt(List.of());
    var service = new DeliveryServiceImpl(null, attempt, new RequestAuditContext());

    assertThrows(
        DeliveryExceptions.ValidationException.class,
        () -> service.acknowledgeDelivery(ACTOR_ID, CONVERSATION_ID, null));
    assertThrows(
        DeliveryExceptions.ValidationException.class,
        () -> service.acknowledgeDelivery(ACTOR_ID, CONVERSATION_ID, -1L));
    assertEquals(0, attempt.calls);
  }

  private static Object deadlock() {
    return DEADLOCK;
  }

  private static final class ScriptedAttempt extends DeliveryAcknowledgementAttempt {
    private final List<?> outcomes;
    private int calls;

    private ScriptedAttempt(List<?> outcomes) {
      super(null);
      this.outcomes = outcomes;
    }

    @Override
    public AcknowledgementAttempt acknowledgeDelivery(
        ConversationId conversationId, UserId actorId, long sequence) {
      return run();
    }

    @Override
    public AcknowledgementAttempt acknowledgeRead(
        ConversationId conversationId, UserId actorId, long sequence) {
      return run();
    }

    private AcknowledgementAttempt run() {
      Object outcome = outcomes.get(calls++);
      if (outcome == DEADLOCK) {
        throw new DeliveryExceptions.DeadlockException(new SQLException("deadlock"));
      }
      return (AcknowledgementAttempt) outcome;
    }
  }
}
