package com.wayden.messenger.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.domain.SessionId;
import io.quarkus.websockets.next.WebSocketConnection;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ConnectionRegistryTest {

  @Test
  void shouldTrackMultipleConnectionsAndReturnStableSnapshots() {
    ConnectionRegistry registry = new ConnectionRegistry();
    UserId userId = new UserId(UUID.randomUUID());
    SessionId sessionId = new SessionId(UUID.randomUUID());
    WebSocketConnection first = connection("first");
    WebSocketConnection second = connection("second");
    Instant expiresAt = Instant.now().plusSeconds(60);
    registry.register(first, userId, sessionId, expiresAt);
    registry.register(second, userId, sessionId, expiresAt);

    var snapshot = registry.connectionsForUser(userId);
    registry.unregister(first);

    assertEquals(2, snapshot.size());
    assertEquals(1, registry.connectionsForUser(userId).size());
    assertEquals(userId, registry.metadataFor(second).orElseThrow().userId());
    registry.unregister(second);
    assertTrue(registry.connectionsForUser(userId).isEmpty());
  }

  @Test
  void shouldContinueClosingRevokedSessionWhenOneConnectionCloseFails() {
    ConnectionRegistry registry = new ConnectionRegistry();
    UserId userId = new UserId(UUID.randomUUID());
    SessionId sessionId = new SessionId(UUID.randomUUID());
    Instant expiresAt = Instant.now().plusSeconds(60);
    AtomicInteger closeAttempts = new AtomicInteger();
    WebSocketConnection failing = connection("failing", closeAttempts, true);
    WebSocketConnection succeeding = connection("succeeding", closeAttempts, false);
    registry.register(failing, userId, sessionId, expiresAt);
    registry.register(succeeding, userId, sessionId, expiresAt);

    registry.closeConnectionsForSession(userId, sessionId, 4401, "Session revoked");

    assertEquals(2, closeAttempts.get());
    assertTrue(registry.connectionsForUser(userId).isEmpty());
    assertTrue(registry.metadataFor(failing).isEmpty());
    assertTrue(registry.metadataFor(succeeding).isEmpty());
  }

  private static WebSocketConnection connection(String id) {
    return connection(id, new AtomicInteger(), false);
  }

  private static WebSocketConnection connection(
      String id, AtomicInteger closeAttempts, boolean failClose) {
    return (WebSocketConnection)
        Proxy.newProxyInstance(
            WebSocketConnection.class.getClassLoader(),
            new Class<?>[] {WebSocketConnection.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("id")) {
                return id;
              }
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("equals")) {
                return proxy == arguments[0];
              }
              if (method.getName().equals("closeAndAwait")) {
                closeAttempts.incrementAndGet();
                if (failClose) {
                  throw new IllegalStateException("synthetic close failure");
                }
              }
              return null;
            });
  }
}
