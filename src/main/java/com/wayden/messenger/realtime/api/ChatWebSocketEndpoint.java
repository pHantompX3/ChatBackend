package com.wayden.messenger.realtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.realtime.application.ConnectionRegistry;
import com.wayden.messenger.realtime.application.WebSocketSessionAuthenticator;
import com.wayden.messenger.realtime.application.WebSocketSessionAuthenticator.AuthenticatedPrincipal;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * WebSocket endpoint at {@code /api/v1/ws}.
 *
 * <p>Handshake authentication: accepts raw token from either:
 *
 * <ol>
 *   <li>Query parameter {@code ?token=<raw>} (parsed from query string)
 *   <li>{@code Sec-WebSocket-Protocol} header: {@code bearer.<raw>} or {@code token.<raw>}
 *   <li>{@code Authorization: Bearer <raw>} header (non-browser clients)
 * </ol>
 *
 * <p>Unauthenticated or expired handshakes are closed immediately with code {@code 4401}.
 */
@WebSocket(path = "/api/v1/ws")
public class ChatWebSocketEndpoint {

  private static final Logger LOG = Logger.getLogger(ChatWebSocketEndpoint.class);
  private static final int CLOSE_UNAUTHORIZED = 4401;
  private static final String BEARER_PREFIX = "bearer.";
  private static final String TOKEN_PREFIX = "token.";

  private final WebSocketSessionAuthenticator authenticator;
  private final ConnectionRegistry registry;
  private final ObjectMapper objectMapper;

  @Inject
  public ChatWebSocketEndpoint(
      WebSocketSessionAuthenticator authenticator,
      ConnectionRegistry registry,
      ObjectMapper objectMapper) {
    this.authenticator = authenticator;
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  @OnOpen
  public void onOpen(WebSocketConnection connection) {
    HandshakeRequest handshake = connection.handshakeRequest();
    Optional<String> rawToken = extractToken(handshake, connection.subprotocol());
    if (rawToken.isEmpty()) {
      LOG.debugf("WebSocket rejected — no token: connectionId=%s", connection.id());
      connection.closeAndAwait(new CloseReason(CLOSE_UNAUTHORIZED, "Authentication required"));
      return;
    }

    Optional<AuthenticatedPrincipal> principal = authenticator.authenticate(rawToken.get());
    if (principal.isEmpty()) {
      LOG.debugf("WebSocket rejected — invalid/expired token: connectionId=%s", connection.id());
      connection.closeAndAwait(
          new CloseReason(CLOSE_UNAUTHORIZED, "Invalid or expired session"));
      return;
    }

    AuthenticatedPrincipal auth = principal.get();
    registry.register(connection, auth.userId(), auth.sessionId());
    LOG.infof(
        "WebSocket connected: connectionId=%s userId=%s", connection.id(), auth.userId().value());
  }

  @OnClose
  public void onClose(WebSocketConnection connection) {
    registry.unregister(connection);
    LOG.debugf("WebSocket closed: connectionId=%s", connection.id());
  }

  @OnError
  public void onError(WebSocketConnection connection, Throwable cause) {
    LOG.warnf(
        cause,
        "WebSocket error: connectionId=%s reason=%s",
        connection.id(),
        cause.getMessage());
    registry.unregister(connection);
  }

  @OnTextMessage
  public void onTextMessage(String message, WebSocketConnection connection) {
    try {
      JsonNode node = objectMapper.readTree(message);
      String type = node.path("type").asText();
      switch (type) {
        case "ping" ->
            // Respond with a pong to satisfy client liveness checks
            connection.sendTextAndAwait("{\"type\":\"pong\"}");
        default ->
            LOG.debugf(
                "Unknown WebSocket command type=%s connectionId=%s", type, connection.id());
      }
    } catch (Exception e) {
      LOG.warnf(e, "Failed to parse WebSocket message from connectionId=%s", connection.id());
    }
  }

  private Optional<String> extractToken(HandshakeRequest handshake, String subprotocol) {
    // 1. Try query parameter (?token=<raw>) — parse from raw query string
    String query = handshake.query();
    if (query != null && !query.isBlank()) {
      String tokenValue = parseQueryParam(query, "token");
      if (tokenValue != null) {
        return Optional.of(tokenValue);
      }
    }

    // 2. Try negotiated subprotocol: bearer.<raw> or token.<raw>
    if (subprotocol != null) {
      if (subprotocol.startsWith(BEARER_PREFIX)) {
        return Optional.of(subprotocol.substring(BEARER_PREFIX.length()));
      }
      if (subprotocol.startsWith(TOKEN_PREFIX)) {
        return Optional.of(subprotocol.substring(TOKEN_PREFIX.length()));
      }
    }

    // 3. Try Sec-WebSocket-Protocol header directly (browser sends all offered protocols)
    String protocolHeader = handshake.header(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
    if (protocolHeader != null) {
      for (String proto : protocolHeader.split(",")) {
        proto = proto.trim();
        if (proto.startsWith(BEARER_PREFIX)) {
          return Optional.of(proto.substring(BEARER_PREFIX.length()));
        }
        if (proto.startsWith(TOKEN_PREFIX)) {
          return Optional.of(proto.substring(TOKEN_PREFIX.length()));
        }
      }
    }

    // 4. Try Authorization header (non-browser clients)
    String authorization = handshake.header("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return Optional.of(authorization.substring("Bearer ".length()));
    }

    return Optional.empty();
  }

  private static String parseQueryParam(String query, String paramName) {
    for (String part : query.split("&")) {
      int eq = part.indexOf('=');
      if (eq < 0) {
        continue;
      }
      String key = part.substring(0, eq);
      String value = part.substring(eq + 1);
      if (paramName.equals(key) && !value.isBlank()) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
