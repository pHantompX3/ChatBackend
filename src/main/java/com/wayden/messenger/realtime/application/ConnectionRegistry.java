package com.wayden.messenger.realtime.application;

import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.domain.SessionId;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * In-memory registry that tracks active WebSocket connections by user ID and connection ID.
 *
 * <p>This is intentionally non-authoritative: it reflects currently connected sockets only and
 * does not survive restarts. Clients must reconcile missed events via REST.
 */
@ApplicationScoped
public class ConnectionRegistry {

  private static final Logger LOG = Logger.getLogger(ConnectionRegistry.class);

  /** Maps userId → set of active connections for that user. */
  private final ConcurrentHashMap<UUID, Set<WebSocketConnection>> userSockets =
      new ConcurrentHashMap<>();

  /** Maps connectionId → ConnectionMetadata for fast lookup during close/revoke. */
  private final ConcurrentHashMap<String, ConnectionMetadata> connectionIndex =
      new ConcurrentHashMap<>();

  public record ConnectionMetadata(
      UserId userId, SessionId sessionId, Instant connectedAt) {}

  /**
   * Registers a new authenticated WebSocket connection.
   *
   * @param connection the Quarkus WebSocket connection
   * @param userId the authenticated user
   * @param sessionId the session backing this connection
   */
  public void register(WebSocketConnection connection, UserId userId, SessionId sessionId) {
    userSockets
        .computeIfAbsent(userId.value(), id -> ConcurrentHashMap.newKeySet())
        .add(connection);
    connectionIndex.put(
        connection.id(), new ConnectionMetadata(userId, sessionId, Instant.now()));
    LOG.debugf("WebSocket registered: connectionId=%s userId=%s", connection.id(), userId.value());
  }

  /**
   * Unregisters a WebSocket connection on close.
   *
   * @param connection the connection that was closed
   */
  public void unregister(WebSocketConnection connection) {
    ConnectionMetadata meta = connectionIndex.remove(connection.id());
    if (meta != null) {
      Set<WebSocketConnection> sockets = userSockets.get(meta.userId().value());
      if (sockets != null) {
        sockets.remove(connection);
        if (sockets.isEmpty()) {
          userSockets.remove(meta.userId().value(), sockets);
        }
      }
      LOG.debugf(
          "WebSocket unregistered: connectionId=%s userId=%s",
          connection.id(), meta.userId().value());
    }
  }

  /**
   * Returns all active connections for a given user, or an empty set if none.
   *
   * @param userId the user to look up
   * @return immutable snapshot of connections
   */
  public Set<WebSocketConnection> connectionsForUser(UserId userId) {
    Set<WebSocketConnection> sockets = userSockets.get(userId.value());
    if (sockets == null || sockets.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(sockets);
  }

  /**
   * Closes all WebSocket connections belonging to a specific session with the given code and
   * reason.
   *
   * @param userId the user whose session is being revoked
   * @param sessionId the specific session being revoked (null means all sessions for user)
   * @param closeCode WebSocket close code (e.g. 4401)
   * @param reason human-readable close reason
   */
  public void closeConnectionsForSession(
      UserId userId, SessionId sessionId, int closeCode, String reason) {
    Map<String, ConnectionMetadata> snapshot = Map.copyOf(connectionIndex);
    for (Map.Entry<String, ConnectionMetadata> entry : snapshot.entrySet()) {
      ConnectionMetadata meta = entry.getValue();
      boolean matches =
          meta.userId().equals(userId)
              && (sessionId == null || meta.sessionId().equals(sessionId));
      if (matches) {
        WebSocketConnection connection = findConnection(meta.userId(), entry.getKey());
        if (connection != null) {
          LOG.infof(
              "Closing WebSocket for revoked session: connectionId=%s userId=%s sessionId=%s code=%d",
              entry.getKey(), userId.value(), sessionId, closeCode);
          connection.closeAndAwait(
              new io.quarkus.websockets.next.CloseReason(closeCode, reason));
        }
      }
    }
  }

  private WebSocketConnection findConnection(UserId userId, String connectionId) {
    Set<WebSocketConnection> sockets = userSockets.get(userId.value());
    if (sockets == null) {
      return null;
    }
    for (WebSocketConnection conn : sockets) {
      if (conn.id().equals(connectionId)) {
        return conn;
      }
    }
    return null;
  }
}
