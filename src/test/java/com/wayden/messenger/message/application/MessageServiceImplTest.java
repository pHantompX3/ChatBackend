package com.wayden.messenger.message.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageService.SendResult;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageBody;
import com.wayden.messenger.message.domain.MessageId;
import com.wayden.messenger.message.domain.MessageType;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MessageServiceImplTest {

  private static final UserId ACTOR_ID = new UserId(UUID.randomUUID());
  private static final ConversationId CONVERSATION_ID = new ConversationId(UUID.randomUUID());
  private static final ClientMessageId CLIENT_MESSAGE_ID = new ClientMessageId(UUID.randomUUID());
  private static final Instant NOW = Instant.parse("2026-08-13T20:00:00Z");

  @Test
  void sendShouldRetryTwoDeadlocksAndReportOnlyPerformedRetries() {
    SendResult accepted = new SendResult(message(), true);
    var attempt =
        new ScriptedSendAttempt(
            List.of(deadlock(), deadlock(), AttemptOutcome.success(accepted)), List.of());
    var audit = new RequestAuditContext();
    var service = service(attempt, audit);

    SendResult result =
        service.send(ACTOR_ID, CONVERSATION_ID, CLIENT_MESSAGE_ID, "synthetic body");

    assertSame(accepted, result);
    assertEquals(3, attempt.attemptCalls);
    assertEquals("2", audit.getCustomAttributes().get("messageDeadlockRetryCount"));
    assertEquals("message.accepted", audit.getCustomAttributes().get("eventType"));
    assertFalse(audit.getCustomAttributes().containsKey("messageDeadlockRetryExhausted"));
  }

  @Test
  void sendShouldResolveTheAcceptedDuplicateWinner() {
    SendResult accepted = new SendResult(message(), false);
    var attempt =
        new ScriptedSendAttempt(
            List.of(
                AttemptOutcome.failure(
                    new MessageExceptions.DuplicateClientMessageException(
                        new SQLException("duplicate")))),
            List.of(AttemptOutcome.success(accepted)));
    var audit = new RequestAuditContext();
    var service = service(attempt, audit);

    SendResult result =
        service.send(ACTOR_ID, CONVERSATION_ID, CLIENT_MESSAGE_ID, "synthetic body");

    assertSame(accepted, result);
    assertEquals(1, attempt.attemptCalls);
    assertEquals(1, attempt.resolveCalls);
    assertEquals("true", audit.getCustomAttributes().get("messageIdempotentRetry"));
    assertEquals("0", audit.getCustomAttributes().get("messageDeadlockRetryCount"));
  }

  @Test
  void sendShouldWrapTheThirdDeadlockAsExhaustionWithoutClaimingAThirdRetry() {
    var attempt = new ScriptedSendAttempt(List.of(deadlock(), deadlock(), deadlock()), List.of());
    var audit = new RequestAuditContext();
    var service = service(attempt, audit);

    MessageExceptions.InternalException failure =
        assertThrows(
            MessageExceptions.InternalException.class,
            () -> service.send(ACTOR_ID, CONVERSATION_ID, CLIENT_MESSAGE_ID, "synthetic body"));

    assertInstanceOf(MessageExceptions.DeadlockException.class, failure.getCause());
    assertEquals(3, attempt.attemptCalls);
    assertEquals("2", audit.getCustomAttributes().get("messageDeadlockRetryCount"));
    assertEquals("true", audit.getCustomAttributes().get("messageDeadlockRetryExhausted"));
  }

  private static MessageServiceImpl service(
      MessageSendAttempt attempt, RequestAuditContext auditContext) {
    return new MessageServiceImpl(null, attempt, Clock.fixed(NOW, ZoneOffset.UTC), auditContext);
  }

  private static Message message() {
    return new Message(
        new MessageId(UUID.randomUUID()),
        CONVERSATION_ID,
        ACTOR_ID,
        CLIENT_MESSAGE_ID,
        1,
        MessageType.TEXT,
        new MessageBody("synthetic body"),
        NOW,
        null,
        null);
  }

  private static AttemptOutcome deadlock() {
    return AttemptOutcome.failure(
        new MessageExceptions.DeadlockException(new SQLException("deadlock")));
  }

  private record AttemptOutcome(SendResult result, RuntimeException failure) {
    private static AttemptOutcome success(SendResult result) {
      return new AttemptOutcome(result, null);
    }

    private static AttemptOutcome failure(RuntimeException failure) {
      return new AttemptOutcome(null, failure);
    }

    private SendResult run() {
      if (failure != null) {
        throw failure;
      }
      return result;
    }
  }

  private static final class ScriptedSendAttempt extends MessageSendAttempt {
    private final List<AttemptOutcome> attempts;
    private final List<AttemptOutcome> resolutions;
    private int attemptCalls;
    private int resolveCalls;

    private ScriptedSendAttempt(List<AttemptOutcome> attempts, List<AttemptOutcome> resolutions) {
      super(null, null, null);
      this.attempts = attempts;
      this.resolutions = resolutions;
    }

    @Override
    public SendResult attempt(SendMessageCommand command) {
      return attempts.get(attemptCalls++).run();
    }

    @Override
    public SendResult resolveAccepted(SendMessageCommand command) {
      return resolutions.get(resolveCalls++).run();
    }
  }
}
