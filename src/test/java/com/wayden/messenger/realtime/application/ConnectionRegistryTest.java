package com.wayden.messenger.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.domain.SessionId;
import io.quarkus.websockets.next.WebSocketConnection;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ConnectionRegistryTest {

  @Test
  void shouldTrackMultipleConnectionsAndReturnStableSnapshots() {
    ConnectionRegistry registry = new ConnectionRegistry();
    UserId userId = new UserId(UUID.randomUUID());
    SessionId sessionId = new SessionId(UUID.randomUUID());
    WebSocketConnection first = connection("first");
    WebSocketConnection second = connection("second");
    registry.register(first, userId, sessionId);
    registry.register(second, userId, sessionId);

    var snapshot = registry.connectionsForUser(userId);
    registry.unregister(first);

    assertEquals(2, snapshot.size());
    assertEquals(1, registry.connectionsForUser(userId).size());
    assertEquals(userId, registry.metadataFor(second).orElseThrow().userId());
    registry.unregister(second);
    assertTrue(registry.connectionsForUser(userId).isEmpty());
  }

  private static WebSocketConnection connection(String id) {
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
              return null;
            });
  }
}
