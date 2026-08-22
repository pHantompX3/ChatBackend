package com.wayden.messenger.realtime.infrastructure;

import com.wayden.messenger.delivery.application.DeliveryEvents;
import com.wayden.messenger.message.application.MessageEvents;
import com.wayden.messenger.realtime.application.ConnectionRegistry;
import com.wayden.messenger.realtime.application.RealtimeEventDispatcher;
import com.wayden.messenger.realtime.domain.RealtimeEventEnvelope;
import com.wayden.messenger.realtime.domain.RealtimeEventType;
import com.wayden.messenger.realtime.domain.RealtimePayloads;
import com.wayden.messenger.session.application.SessionEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Observes post-commit CDI events and fans out the corresponding realtime frames over WebSocket.
 *
 * <p><strong>Invariant:</strong> observers use {@link TransactionPhase#AFTER_SUCCESS} so that
 * frames are only dispatched after the database transaction has durably committed. This ensures
 * WebSocket frames are never ahead of the SQL Server source of truth.
 */
@ApplicationScoped
public class RealtimePostCommitObserver {

  private static final Logger LOG = Logger.getLogger(RealtimePostCommitObserver.class);

  private final RealtimeEventDispatcher dispatcher;
  private final ConnectionRegistry connectionRegistry;

  @Inject
  public RealtimePostCommitObserver(
      RealtimeEventDispatcher dispatcher, ConnectionRegistry connectionRegistry) {
    this.dispatcher = dispatcher;
    this.connectionRegistry = connectionRegistry;
  }

  public void onMessageCreated(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEvents.MessageCreatedEvent event) {
    try {
      RealtimePayloads.MessageCreatedPayload payload =
          new RealtimePayloads.MessageCreatedPayload(
              event.conversationId().value(),
              event.messageId().value(),
              event.sequenceNumber(),
              event.senderId().value(),
              event.clientMessageId().value(),
              event.body(),
              event.createdAt());
      dispatcher.dispatch(
          event.conversationId(),
          new RealtimeEventEnvelope(
              UUID.randomUUID(),
              RealtimeEventType.MESSAGE_CREATED.typeName(),
              Instant.now(),
              event.conversationId().value(),
              payload));
    } catch (Exception e) {
      LOG.errorf(e, "Error in post-commit observer for message.created: %s", e.getMessage());
    }
  }

  public void onMessageEdited(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEvents.MessageEditedEvent event) {
    try {
      RealtimePayloads.MessageEditedPayload payload =
          new RealtimePayloads.MessageEditedPayload(
              event.conversationId().value(),
              event.messageId().value(),
              event.sequenceNumber(),
              event.body(),
              event.editedAt());
      dispatcher.dispatch(
          event.conversationId(),
          new RealtimeEventEnvelope(
              UUID.randomUUID(),
              RealtimeEventType.MESSAGE_EDITED.typeName(),
              Instant.now(),
              event.conversationId().value(),
              payload));
    } catch (Exception e) {
      LOG.errorf(e, "Error in post-commit observer for message.edited: %s", e.getMessage());
    }
  }

  public void onMessageDeleted(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEvents.MessageDeletedEvent event) {
    try {
      RealtimePayloads.MessageDeletedPayload payload =
          new RealtimePayloads.MessageDeletedPayload(
              event.conversationId().value(),
              event.messageId().value(),
              event.sequenceNumber(),
              event.deletedAt());
      dispatcher.dispatch(
          event.conversationId(),
          new RealtimeEventEnvelope(
              UUID.randomUUID(),
              RealtimeEventType.MESSAGE_DELETED.typeName(),
              Instant.now(),
              event.conversationId().value(),
              payload));
    } catch (Exception e) {
      LOG.errorf(e, "Error in post-commit observer for message.deleted: %s", e.getMessage());
    }
  }

  public void onDeliveryAcknowledged(
      @Observes(during = TransactionPhase.AFTER_SUCCESS)
          DeliveryEvents.DeliveryAcknowledgedEvent event) {
    try {
      RealtimePayloads.DeliveryUpdatedPayload payload =
          new RealtimePayloads.DeliveryUpdatedPayload(
              event.conversationId().value(),
              event.userId().value(),
              event.lastDeliveredSequence(),
              event.updatedAt());
      dispatcher.dispatch(
          event.conversationId(),
          new RealtimeEventEnvelope(
              UUID.randomUUID(),
              RealtimeEventType.DELIVERY_UPDATED.typeName(),
              Instant.now(),
              event.conversationId().value(),
              payload));
    } catch (Exception e) {
      LOG.errorf(e, "Error in post-commit observer for delivery.updated: %s", e.getMessage());
    }
  }

  public void onReadAcknowledged(
      @Observes(during = TransactionPhase.AFTER_SUCCESS)
          DeliveryEvents.ReadAcknowledgedEvent event) {
    try {
      RealtimePayloads.ReadUpdatedPayload payload =
          new RealtimePayloads.ReadUpdatedPayload(
              event.conversationId().value(),
              event.userId().value(),
              event.lastReadSequence(),
              event.lastDeliveredSequence(),
              event.updatedAt());
      dispatcher.dispatch(
          event.conversationId(),
          new RealtimeEventEnvelope(
              UUID.randomUUID(),
              RealtimeEventType.READ_UPDATED.typeName(),
              Instant.now(),
              event.conversationId().value(),
              payload));
    } catch (Exception e) {
      LOG.errorf(e, "Error in post-commit observer for read.updated: %s", e.getMessage());
    }
  }

  public void onSessionRevoked(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) SessionEvents.SessionRevokedEvent event) {
    try {
      connectionRegistry.closeConnectionsForSession(
          event.userId(), event.sessionId(), 4401, "Session revoked");
    } catch (Exception e) {
      LOG.errorf(
          e,
          "Error in post-commit observer for session.revoked userId=%s sessionId=%s",
          event.userId().value(),
          event.sessionId().value());
    }
  }

  public void onAllSessionsRevoked(
      @Observes(during = TransactionPhase.AFTER_SUCCESS)
          SessionEvents.AllSessionsRevokedEvent event) {
    try {
      connectionRegistry.closeConnectionsForSession(
          event.userId(), null, 4401, "All sessions revoked");
    } catch (Exception e) {
      LOG.errorf(
          e,
          "Error in post-commit observer for session.revoked.all userId=%s",
          event.userId().value());
    }
  }
}
