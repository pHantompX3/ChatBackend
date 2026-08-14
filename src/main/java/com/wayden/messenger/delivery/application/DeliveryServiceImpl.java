package com.wayden.messenger.delivery.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryRepository.AcknowledgementAttempt;
import com.wayden.messenger.delivery.application.DeliveryRepository.StatusLookup;
import com.wayden.messenger.delivery.domain.AcknowledgementResult;
import com.wayden.messenger.delivery.domain.MessageDeliveryStatus;
import com.wayden.messenger.delivery.domain.MessagePosition;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.MessageId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DeliveryServiceImpl implements DeliveryService {

  private static final Logger LOG = Logger.getLogger(DeliveryServiceImpl.class);
  private static final int MAX_ACKNOWLEDGEMENT_ATTEMPTS = 3;

  private final DeliveryRepository repository;
  private final DeliveryAcknowledgementAttempt acknowledgementAttempt;
  private final RequestAuditContext auditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Request audit context is container-managed request state.")
  public DeliveryServiceImpl(
      DeliveryRepository repository,
      DeliveryAcknowledgementAttempt acknowledgementAttempt,
      RequestAuditContext auditContext) {
    this.repository = repository;
    this.acknowledgementAttempt = acknowledgementAttempt;
    this.auditContext = auditContext;
  }

  @Override
  public void acknowledgeDelivery(UserId actorId, ConversationId conversationId, Long rawSequence) {
    auditConversation(conversationId);
    acknowledge(actorId, conversationId, sequence(rawSequence), false);
  }

  @Override
  public void acknowledgeRead(UserId actorId, ConversationId conversationId, Long rawSequence) {
    auditConversation(conversationId);
    acknowledge(actorId, conversationId, sequence(rawSequence), true);
  }

  @Override
  @Transactional
  public MessagePosition getPosition(UserId actorId, ConversationId conversationId) {
    auditConversation(conversationId);
    MessagePosition position =
        repository
            .findPosition(conversationId, actorId)
            .orElseThrow(DeliveryExceptions.ResourceNotFoundException::new);
    auditContext.putCustomAttribute("eventType", "delivery.position.queried");
    auditContext.putCustomAttribute("latestSequence", Long.toString(position.latestSequence()));
    auditContext.putCustomAttribute(
        "currentDeliveredSequence", Long.toString(position.lastDeliveredSequence()));
    auditContext.putCustomAttribute(
        "currentReadSequence", Long.toString(position.lastReadSequence()));
    auditContext.putCustomAttribute("unreadCount", Long.toString(position.unreadCount()));
    return position;
  }

  @Override
  @Transactional
  public MessageDeliveryStatus getStatus(
      UserId actorId, ConversationId conversationId, MessageId messageId) {
    auditConversation(conversationId);
    auditContext.putCustomAttribute("targetMessageId", messageId.value().toString());
    StatusLookup lookup = repository.findSenderStatus(conversationId, messageId, actorId);
    if (lookup instanceof StatusLookup.ResourceNotFound) {
      throw new DeliveryExceptions.ResourceNotFoundException();
    }
    if (lookup instanceof StatusLookup.Forbidden) {
      throw new DeliveryExceptions.StatusForbiddenException();
    }
    MessageDeliveryStatus status = ((StatusLookup.Found) lookup).status();
    auditContext.putCustomAttribute("eventType", "message.delivery-status.queried");
    auditContext.putCustomAttribute("recipientCount", Long.toString(status.recipientCount()));
    auditContext.putCustomAttribute("deliveredCount", Long.toString(status.deliveredCount()));
    auditContext.putCustomAttribute("readCount", Long.toString(status.readCount()));
    return status;
  }

  private void acknowledge(
      UserId actorId, ConversationId conversationId, long requestedSequence, boolean read) {
    auditContext.putCustomAttribute("requestedSequence", Long.toString(requestedSequence));
    DeliveryExceptions.DeadlockException lastDeadlock = null;
    for (int attemptNumber = 1; attemptNumber <= MAX_ACKNOWLEDGEMENT_ATTEMPTS; attemptNumber++) {
      try {
        AcknowledgementAttempt attempt =
            read
                ? acknowledgementAttempt.acknowledgeRead(conversationId, actorId, requestedSequence)
                : acknowledgementAttempt.acknowledgeDelivery(
                    conversationId, actorId, requestedSequence);
        handleAttempt(attempt, conversationId, requestedSequence, read, attemptNumber - 1);
        return;
      } catch (DeliveryExceptions.DeadlockException deadlock) {
        lastDeadlock = deadlock;
      }

      if (attemptNumber < MAX_ACKNOWLEDGEMENT_ATTEMPTS) {
        auditContext.putCustomAttribute(
            "deliveryDeadlockRetryCount", Integer.toString(attemptNumber));
        LOG.warnf(
            "Delivery acknowledgement deadlock requestId=%s conversationId=%s sequence=%d "
                + "attempt=%d outcome=retry",
            auditContext.getRequestId(), conversationId.value(), requestedSequence, attemptNumber);
        backoff();
      } else {
        auditContext.putCustomAttribute(
            "deliveryDeadlockRetryCount", Integer.toString(MAX_ACKNOWLEDGEMENT_ATTEMPTS - 1));
        auditContext.putCustomAttribute("deliveryDeadlockRetryExhausted", "true");
      }
    }
    throw new DeliveryExceptions.InternalException(
        "acknowledge the conversation position after bounded deadlock retries", lastDeadlock);
  }

  private void handleAttempt(
      AcknowledgementAttempt attempt,
      ConversationId conversationId,
      long requestedSequence,
      boolean read,
      int retryCount) {
    if (attempt instanceof AcknowledgementAttempt.ResourceNotFound) {
      throw new DeliveryExceptions.ResourceNotFoundException();
    }
    if (attempt instanceof AcknowledgementAttempt.SequenceAhead sequenceAhead) {
      auditContext.putCustomAttribute(
          "latestSequence", Long.toString(sequenceAhead.latestSequence()));
      throw new DeliveryExceptions.SequenceAheadException();
    }
    AcknowledgementResult result = ((AcknowledgementAttempt.Acknowledged) attempt).result();
    auditContext.putCustomAttribute(
        "eventType", read ? "read.position.acknowledged" : "delivery.position.acknowledged");
    auditContext.putCustomAttribute("latestSequence", Long.toString(result.latestSequence()));
    auditContext.putCustomAttribute(
        "previousDeliveredSequence", Long.toString(result.previousDeliveredSequence()));
    auditContext.putCustomAttribute(
        "currentDeliveredSequence", Long.toString(result.currentDeliveredSequence()));
    auditContext.putCustomAttribute(
        "previousReadSequence", Long.toString(result.previousReadSequence()));
    auditContext.putCustomAttribute(
        "currentReadSequence", Long.toString(result.currentReadSequence()));
    auditContext.putCustomAttribute("deliveryOutcome", result.outcome().name());
    auditContext.putCustomAttribute("deliveryDeadlockRetryCount", Integer.toString(retryCount));
  }

  private static long sequence(Long rawSequence) {
    if (rawSequence == null) {
      throw new DeliveryExceptions.ValidationException("sequence must not be null");
    }
    if (rawSequence < 0) {
      throw new DeliveryExceptions.ValidationException("sequence must not be negative");
    }
    return rawSequence;
  }

  private void auditConversation(ConversationId conversationId) {
    auditContext.putCustomAttribute("targetConversationId", conversationId.value().toString());
  }

  private static void backoff() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(5, 26));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DeliveryExceptions.InternalException(
          "wait before retrying delivery acknowledgement", exception);
    }
  }
}
