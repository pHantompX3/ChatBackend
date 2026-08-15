package com.wayden.messenger.message.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.ConversationType;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageRepository.ActorAccess;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageBody;
import com.wayden.messenger.message.domain.MessageId;
import com.wayden.messenger.message.domain.MessageType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MessageServiceImpl implements MessageService {

  private static final Logger LOG = Logger.getLogger(MessageServiceImpl.class);
  private static final int MAX_SEND_ATTEMPTS = 3;

  private final MessageRepository repository;
  private final MessageSendAttempt sendAttempt;
  private final Clock clock;
  private final RequestAuditContext auditContext;
  private final jakarta.enterprise.event.Event<MessageEvents.MessageEditedEvent> messageEditedEvent;
  private final jakarta.enterprise.event.Event<MessageEvents.MessageDeletedEvent> messageDeletedEvent;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Clock and request audit context are container-managed collaborators.")
  public MessageServiceImpl(
      MessageRepository repository,
      MessageSendAttempt sendAttempt,
      Clock clock,
      RequestAuditContext auditContext,
      jakarta.enterprise.event.Event<MessageEvents.MessageEditedEvent> messageEditedEvent,
      jakarta.enterprise.event.Event<MessageEvents.MessageDeletedEvent> messageDeletedEvent) {
    this.repository = repository;
    this.sendAttempt = sendAttempt;
    this.clock = clock;
    this.auditContext = auditContext;
    this.messageEditedEvent = messageEditedEvent;
    this.messageDeletedEvent = messageDeletedEvent;
  }

  @Override
  public SendResult send(
      UserId actorId,
      ConversationId conversationId,
      ClientMessageId clientMessageId,
      String rawBody) {
    MessageBody body = body(rawBody);
    SendMessageCommand command =
        new SendMessageCommand(actorId, conversationId, clientMessageId, body);
    MessageExceptions.DeadlockException lastDeadlock = null;

    for (int attemptNumber = 1; attemptNumber <= MAX_SEND_ATTEMPTS; attemptNumber++) {
      try {
        SendResult result = sendAttempt.attempt(command);
        auditSend(result, attemptNumber - 1);
        return result;
      } catch (MessageExceptions.DuplicateClientMessageException duplicate) {
        try {
          SendResult result = sendAttempt.resolveAccepted(command);
          auditSend(result, attemptNumber - 1);
          return result;
        } catch (MessageExceptions.DeadlockException deadlock) {
          lastDeadlock = deadlock;
        }
      } catch (MessageExceptions.DeadlockException deadlock) {
        lastDeadlock = deadlock;
      }

      if (attemptNumber < MAX_SEND_ATTEMPTS) {
        auditContext.putCustomAttribute(
            "messageDeadlockRetryCount", Integer.toString(attemptNumber));
        LOG.warnf(
            "Message send deadlock requestId=%s conversationId=%s attempt=%d outcome=retry",
            auditContext.getRequestId(), conversationId.value(), attemptNumber);
        backoff();
      } else {
        auditContext.putCustomAttribute(
            "messageDeadlockRetryCount", Integer.toString(MAX_SEND_ATTEMPTS - 1));
        auditContext.putCustomAttribute("messageDeadlockRetryExhausted", "true");
        LOG.warnf(
            "Message send deadlock requestId=%s conversationId=%s attempt=%d outcome=exhausted",
            auditContext.getRequestId(), conversationId.value(), attemptNumber);
      }
    }

    throw new MessageExceptions.InternalException(
        "send message after bounded deadlock retries", lastDeadlock);
  }

  @Override
  @Transactional
  public MessagePage list(
      UserId actorId, ConversationId conversationId, Long rawAfterSequence, Integer rawLimit) {
    long afterSequence = rawAfterSequence == null ? 0 : rawAfterSequence;
    int limit = rawLimit == null ? 50 : rawLimit;
    if (afterSequence < 0) {
      throw new MessageExceptions.ValidationException("afterSequence must not be negative");
    }
    if (limit < 1 || limit > 200) {
      throw new MessageExceptions.ValidationException("limit must be between 1 and 200");
    }
    requireAccess(conversationId, actorId, false);
    List<Message> rows = repository.listAfter(conversationId, actorId, afterSequence, limit + 1);
    boolean hasNext = rows.size() > limit;
    List<Message> items = hasNext ? rows.subList(0, limit) : rows;
    Long nextAfterSequence = hasNext ? items.get(items.size() - 1).sequenceNumber() : null;
    auditContext.putCustomAttribute("eventType", "message.history.listed");
    auditContext.putCustomAttribute("targetConversationId", conversationId.value().toString());
    return new MessagePage(items, nextAfterSequence);
  }

  @Override
  @Transactional
  public Message edit(
      UserId actorId, ConversationId conversationId, MessageId messageId, String replacementBody) {
    MessageBody body = body(replacementBody);
    requireAccess(conversationId, actorId, true);
    Message current = requireMessage(conversationId, messageId, true);
    if (!current.senderId().equals(actorId)
        || current.type() != MessageType.TEXT
        || current.isDeleted()) {
      throw new MessageExceptions.EditForbiddenException();
    }
    Message edited;
    try {
      edited = current.edit(body, clock.instant());
    } catch (IllegalStateException exception) {
      throw new MessageExceptions.EditForbiddenException();
    }
    if (edited == current) {
      auditMutation("message.edited", conversationId, current, false);
      return current;
    }
    if (!repository.edit(messageId, body, edited.editedAt())) {
      Message winner = requireMessage(conversationId, messageId, true);
      if (winner.isDeleted()) {
        throw new MessageExceptions.EditForbiddenException();
      }
      throw new MessageExceptions.InternalException(
          "edit message", new IllegalStateException("Conditional message edit changed no rows"));
    }
    auditMutation("message.edited", conversationId, edited, false);
    messageEditedEvent.fire(MessageEvents.MessageEditedEvent.from(edited));
    return edited;
  }

  @Override
  @Transactional
  public void delete(UserId actorId, ConversationId conversationId, MessageId messageId) {
    ActorAccess access = requireAccess(conversationId, actorId, true);
    Message current = requireMessage(conversationId, messageId, true);
    boolean administrative = authorizeDelete(actorId, access, current);
    if (!current.isDeleted()) {
      Instant deletedAt = clock.instant();
      if (!repository.softDelete(messageId, deletedAt)) {
        Message winner = requireMessage(conversationId, messageId, true);
        if (!winner.isDeleted()) {
          throw new MessageExceptions.InternalException(
              "soft-delete message",
              new IllegalStateException("Conditional message deletion changed no rows"));
        }
      }
    }
    Message latestForEvent = current.isDeleted() ? current : requireMessage(conversationId, messageId, false);
    auditMutation(
        administrative ? "message.administratively.deleted" : "message.deleted",
        conversationId,
        current,
        administrative);
    if (latestForEvent.isDeleted()) {
      messageDeletedEvent.fire(MessageEvents.MessageDeletedEvent.from(latestForEvent));
    }
  }

  private ActorAccess requireAccess(
      ConversationId conversationId, UserId actorId, boolean lockForMutation) {
    return repository
        .findActiveAccess(conversationId, actorId, lockForMutation)
        .orElseThrow(MessageExceptions.AccessDeniedException::new);
  }

  private Message requireMessage(
      ConversationId conversationId, MessageId messageId, boolean lockForMutation) {
    return repository
        .findById(conversationId, messageId, lockForMutation)
        .orElseThrow(MessageExceptions.AccessDeniedException::new);
  }

  private static boolean authorizeDelete(UserId actorId, ActorAccess access, Message message) {
    if (message.senderId().equals(actorId)) {
      return false;
    }
    boolean groupManager =
        access.conversationType() == ConversationType.GROUP
            && (access.role() == ConversationRole.OWNER || access.role() == ConversationRole.ADMIN);
    if (!groupManager) {
      throw new MessageExceptions.DeleteForbiddenException();
    }
    return true;
  }

  private void auditSend(SendResult result, int retryCount) {
    Message message = result.message();
    auditContext.putCustomAttribute("eventType", "message.accepted");
    auditContext.putCustomAttribute(
        "targetConversationId", message.conversationId().value().toString());
    auditContext.putCustomAttribute("targetMessageId", message.id().value().toString());
    auditContext.putCustomAttribute("messageSenderId", message.senderId().value().toString());
    auditContext.putCustomAttribute(
        "clientMessageId", message.clientMessageId().value().toString());
    auditContext.putCustomAttribute(
        "messageSequenceNumber", Long.toString(message.sequenceNumber()));
    auditContext.putCustomAttribute("messageType", message.type().name());
    auditContext.putCustomAttribute("messageIdempotentRetry", Boolean.toString(!result.created()));
    auditContext.putCustomAttribute("messageDeadlockRetryCount", Integer.toString(retryCount));
  }

  private void auditMutation(
      String eventType, ConversationId conversationId, Message message, boolean administrative) {
    auditContext.putCustomAttribute("eventType", eventType);
    auditContext.putCustomAttribute("targetConversationId", conversationId.value().toString());
    auditContext.putCustomAttribute("targetMessageId", message.id().value().toString());
    auditContext.putCustomAttribute("messageSenderId", message.senderId().value().toString());
    auditContext.putCustomAttribute(
        "messageSequenceNumber", Long.toString(message.sequenceNumber()));
    auditContext.putCustomAttribute(
        "messageAdministrativeDelete", Boolean.toString(administrative));
  }

  private static MessageBody body(String rawBody) {
    try {
      return new MessageBody(rawBody);
    } catch (IllegalArgumentException exception) {
      throw new MessageExceptions.ValidationException(exception.getMessage(), exception);
    }
  }

  private static void backoff() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(5, 26));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new MessageExceptions.InternalException("wait before retrying message send", exception);
    }
  }
}
