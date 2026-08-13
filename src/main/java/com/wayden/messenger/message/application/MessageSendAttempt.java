package com.wayden.messenger.message.application;

import com.wayden.messenger.message.application.MessageService.SendResult;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;

@ApplicationScoped
public class MessageSendAttempt {

  private final MessageRepository repository;
  private final MessageIdGenerator idGenerator;
  private final Clock clock;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Clock is a container-managed collaborator.")
  public MessageSendAttempt(
      MessageRepository repository, MessageIdGenerator idGenerator, Clock clock) {
    this.repository = repository;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public SendResult attempt(SendMessageCommand command) {
    requireAccess(command);
    var existing = repository.findByClientMessageId(command.senderId(), command.clientMessageId());
    if (existing.isPresent()) {
      return resolve(command, existing.orElseThrow());
    }

    Instant now = clock.instant();
    long sequence = repository.allocateSequence(command.conversationId(), now);
    Message message =
        new Message(
            idGenerator.newMessageId(),
            command.conversationId(),
            command.senderId(),
            command.clientMessageId(),
            sequence,
            MessageType.TEXT,
            command.body(),
            now,
            null,
            null);
    repository.insert(message);
    return new SendResult(message, true);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public SendResult resolveAccepted(SendMessageCommand command) {
    requireAccess(command);
    Message existing =
        repository
            .findByClientMessageId(command.senderId(), command.clientMessageId())
            .orElseThrow(
                () ->
                    new MessageExceptions.InternalException(
                        "resolve the accepted idempotent message",
                        new IllegalStateException("Duplicate key winner was not visible")));
    return resolve(command, existing);
  }

  private void requireAccess(SendMessageCommand command) {
    repository
        .findActiveAccess(command.conversationId(), command.senderId(), true)
        .orElseThrow(MessageExceptions.AccessDeniedException::new);
  }

  private static SendResult resolve(SendMessageCommand command, Message existing) {
    if (!existing.conversationId().equals(command.conversationId())) {
      throw new MessageExceptions.IdempotencyConflictException();
    }
    return new SendResult(existing, false);
  }
}
