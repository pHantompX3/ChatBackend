package com.wayden.messenger.realtime.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.conversation.application.ConversationRepository;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.realtime.domain.RealtimeEventEnvelope;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Dispatches realtime event envelopes to the connected sockets of active conversation members.
 *
 * <p>Privacy guarantee: events are only sent to users who are current active members of the
 * conversation ({@code left_at IS NULL}). Non-members and departed members receive nothing.
 */
@ApplicationScoped
public class RealtimeEventDispatcher {

  private static final Logger LOG = Logger.getLogger(RealtimeEventDispatcher.class);

  private final ConversationRepository conversationRepository;
  private final ConnectionRegistry connectionRegistry;
  private final ObjectMapper objectMapper;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are container-managed application services.")
  public RealtimeEventDispatcher(
      ConversationRepository conversationRepository,
      ConnectionRegistry connectionRegistry,
      ObjectMapper objectMapper) {
    this.conversationRepository = conversationRepository;
    this.connectionRegistry = connectionRegistry;
    this.objectMapper = objectMapper;
  }

  /**
   * Fans out an envelope to all connected active members of a conversation.
   *
   * @param conversationId the conversation the event belongs to
   * @param envelope the serialisable event envelope
   */
  public void dispatch(ConversationId conversationId, RealtimeEventEnvelope envelope) {
    String json;
    try {
      json = objectMapper.writeValueAsString(envelope);
    } catch (Exception e) {
      LOG.errorf(
          e,
          "Failed to serialise realtime envelope eventType=%s conversationId=%s",
          envelope.eventType(),
          conversationId.value());
      return;
    }

    java.util.List<UserId> activeMembers;
    try {
      activeMembers = conversationRepository.findActiveMemberUserIds(conversationId);
    } catch (Exception e) {
      LOG.errorf(
          e,
          "Failed to query active members for realtime fanout conversationId=%s",
          conversationId.value());
      return;
    }

    for (UserId memberId : activeMembers) {
      Set<WebSocketConnection> connections = connectionRegistry.connectionsForUser(memberId);
      for (WebSocketConnection connection : connections) {
        if (!connectionRegistry.isActive(connection, Instant.now())) {
          continue;
        }
        send(connection, memberId, envelope.eventType(), json);
      }
    }
  }

  private static void send(
      WebSocketConnection connection, UserId userId, String eventType, String json) {
    try {
      connection
          .sendText(json)
          .subscribe()
          .with(ignored -> {}, failure -> logSendFailure(failure, connection, userId, eventType));
    } catch (Exception failure) {
      logSendFailure(failure, connection, userId, eventType);
    }
  }

  private static void logSendFailure(
      Throwable failure, WebSocketConnection connection, UserId userId, String eventType) {
    LOG.warnf(
        failure,
        "Failed to send realtime frame to connectionId=%s userId=%s eventType=%s",
        connection.id(),
        userId.value(),
        eventType);
  }
}
