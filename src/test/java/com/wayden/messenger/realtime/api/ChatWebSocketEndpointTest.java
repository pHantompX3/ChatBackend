package com.wayden.messenger.realtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.delivery.application.DeliveryService;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.realtime.application.ConnectionRegistry;
import com.wayden.messenger.realtime.application.WebSocketSessionAuthenticator;
import com.wayden.messenger.realtime.application.WebSocketSessionAuthenticator.AuthenticatedPrincipal;
import com.wayden.messenger.session.domain.SessionId;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.WebSocketConnection;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ChatWebSocketEndpointTest {

  private static final Instant EXPIRES_AT = Instant.now().plusSeconds(300);
  private static final UserId USER_ID = new UserId(UUID.randomUUID());
  private static final SessionId SESSION_ID = new SessionId(UUID.randomUUID());
  private static final AuthenticatedPrincipal PRINCIPAL =
      new AuthenticatedPrincipal(USER_ID, SESSION_ID, EXPIRES_AT);

  @Test
  void shouldAcceptCaseInsensitiveTrimmedBearerHeaderAndRegisterConnection() {
    ConnectionRegistry registry = new ConnectionRegistry();
    List<String> tokens = new ArrayList<>();
    ChatWebSocketEndpoint endpoint =
        endpoint(
            token -> {
              tokens.add(token);
              return Optional.of(PRINCIPAL);
            },
            registry,
            noOpDeliveryService());
    SocketProbe socket = socket("accepted", "bearer valid-token   ");

    endpoint.onOpen(socket.connection());

    assertEquals(List.of("valid-token", "valid-token"), tokens);
    assertTrue(registry.metadataFor(socket.connection()).isPresent());
    assertTrue(socket.closeReasons().isEmpty());
  }

  @Test
  void shouldRejectSessionRevokedBetweenAuthenticationAndRegistration() {
    ConnectionRegistry registry = new ConnectionRegistry();
    AtomicInteger attempts = new AtomicInteger();
    ChatWebSocketEndpoint endpoint =
        endpoint(
            token -> attempts.getAndIncrement() == 0 ? Optional.of(PRINCIPAL) : Optional.empty(),
            registry,
            noOpDeliveryService());
    SocketProbe socket = socket("revoked-during-open", "Bearer valid-token");

    endpoint.onOpen(socket.connection());

    assertEquals(4401, socket.closeReasons().getFirst().getCode());
    assertTrue(registry.metadataFor(socket.connection()).isEmpty());
  }

  @Test
  void shouldCloseUnregisteredConnectionInsteadOfReturningCommandError() {
    SocketProbe socket = socket("unregistered", null);
    ChatWebSocketEndpoint endpoint =
        endpoint(token -> Optional.of(PRINCIPAL), new ConnectionRegistry(), noOpDeliveryService());

    endpoint.onTextMessage("{\"action\":\"ping\"}", socket.connection());

    assertEquals(4401, socket.closeReasons().getFirst().getCode());
    assertTrue(socket.frames().isEmpty());
  }

  @Test
  void shouldRejectFractionalAndOutOfRangeSequencesWithoutCallingDeliveryService() {
    ConnectionRegistry registry = new ConnectionRegistry();
    AtomicInteger acknowledgements = new AtomicInteger();
    DeliveryService deliveryService = deliveryService(acknowledgements);
    ChatWebSocketEndpoint endpoint =
        endpoint(token -> Optional.of(PRINCIPAL), registry, deliveryService);
    SocketProbe socket = socket("invalid-sequence", null);
    registry.register(socket.connection(), USER_ID, SESSION_ID, EXPIRES_AT);
    String conversationId = UUID.randomUUID().toString();

    endpoint.onTextMessage(
        acknowledgement("delivery.ack", conversationId, "1.9"), socket.connection());
    endpoint.onTextMessage(
        acknowledgement("read.ack", conversationId, "9223372036854775808"), socket.connection());

    assertEquals(0, acknowledgements.get());
    assertEquals(
        List.of(
            "{\"type\":\"error\",\"code\":\"INVALID_COMMAND\"}",
            "{\"type\":\"error\",\"code\":\"INVALID_COMMAND\"}"),
        socket.frames());
  }

  @Test
  void shouldDelegateValidIntegralAcknowledgement() {
    ConnectionRegistry registry = new ConnectionRegistry();
    AtomicInteger acknowledgements = new AtomicInteger();
    ChatWebSocketEndpoint endpoint =
        endpoint(token -> Optional.of(PRINCIPAL), registry, deliveryService(acknowledgements));
    SocketProbe socket = socket("valid-sequence", null);
    registry.register(socket.connection(), USER_ID, SESSION_ID, EXPIRES_AT);

    endpoint.onTextMessage(
        acknowledgement("delivery.ack", UUID.randomUUID().toString(), "42"), socket.connection());

    assertEquals(1, acknowledgements.get());
    assertTrue(socket.frames().isEmpty());
  }

  private static String acknowledgement(String action, String conversationId, String sequence) {
    return "{\"action\":\""
        + action
        + "\",\"conversationId\":\""
        + conversationId
        + "\",\"sequence\":"
        + sequence
        + "}";
  }

  private static ChatWebSocketEndpoint endpoint(
      Authentication authentication, ConnectionRegistry registry, DeliveryService deliveryService) {
    WebSocketSessionAuthenticator authenticator =
        new WebSocketSessionAuthenticator(null, null, Clock.systemUTC()) {
          @Override
          public Optional<AuthenticatedPrincipal> authenticate(String rawToken) {
            return authentication.authenticate(rawToken);
          }
        };
    return new ChatWebSocketEndpoint(
        authenticator, registry, new ObjectMapper().findAndRegisterModules(), deliveryService);
  }

  private static DeliveryService noOpDeliveryService() {
    return deliveryService(new AtomicInteger());
  }

  private static DeliveryService deliveryService(AtomicInteger acknowledgements) {
    return (DeliveryService)
        Proxy.newProxyInstance(
            DeliveryService.class.getClassLoader(),
            new Class<?>[] {DeliveryService.class},
            (proxy, method, arguments) -> {
              if (method.getName().startsWith("acknowledge")) {
                acknowledgements.incrementAndGet();
              }
              return null;
            });
  }

  private static SocketProbe socket(String id, String authorization) {
    List<String> frames = new ArrayList<>();
    List<CloseReason> closeReasons = new ArrayList<>();
    HandshakeRequest handshake =
        (HandshakeRequest)
            Proxy.newProxyInstance(
                HandshakeRequest.class.getClassLoader(),
                new Class<?>[] {HandshakeRequest.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "query" -> null;
                      case "header" -> "Authorization".equals(arguments[0]) ? authorization : null;
                      case "headers" ->
                          arguments == null || arguments.length == 0 ? Map.of() : List.of();
                      default -> defaultValue(method.getReturnType());
                    });
    AtomicReference<WebSocketConnection> reference = new AtomicReference<>();
    WebSocketConnection connection =
        (WebSocketConnection)
            Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class<?>[] {WebSocketConnection.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "id" -> id;
                      case "handshakeRequest" -> handshake;
                      case "subprotocol" -> null;
                      case "sendTextAndAwait" -> {
                        frames.add((String) arguments[0]);
                        yield null;
                      }
                      case "closeAndAwait" -> {
                        closeReasons.add((CloseReason) arguments[0]);
                        yield null;
                      }
                      case "hashCode" -> System.identityHashCode(reference.get());
                      case "equals" -> proxy == arguments[0];
                      default -> defaultValue(method.getReturnType());
                    });
    reference.set(connection);
    return new SocketProbe(connection, frames, closeReasons);
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    return null;
  }

  private record SocketProbe(
      WebSocketConnection connection, List<String> frames, List<CloseReason> closeReasons) {}

  @FunctionalInterface
  private interface Authentication {
    Optional<AuthenticatedPrincipal> authenticate(String token);
  }
}
