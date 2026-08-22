package com.wayden.messenger.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.conversation.application.ConversationRepository;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.realtime.domain.RealtimeEventEnvelope;
import com.wayden.messenger.session.domain.SessionId;
import io.quarkus.websockets.next.WebSocketConnection;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RealtimeEventDispatcherTest {

  @Test
  void shouldFanOutToEveryActiveMemberConnectionAndExcludeNonMembers() {
    UserId member = new UserId(UUID.randomUUID());
    UserId outsider = new UserId(UUID.randomUUID());
    ConversationId conversationId = new ConversationId(UUID.randomUUID());
    List<String> memberFrames = new ArrayList<>();
    List<String> secondDeviceFrames = new ArrayList<>();
    List<String> outsiderFrames = new ArrayList<>();
    ConnectionRegistry registry = new ConnectionRegistry();
    registry.register(
        connection("member-1", memberFrames), member, new SessionId(UUID.randomUUID()));
    registry.register(
        connection("member-2", secondDeviceFrames), member, new SessionId(UUID.randomUUID()));
    registry.register(
        connection("outsider", outsiderFrames), outsider, new SessionId(UUID.randomUUID()));
    ConversationRepository repository = activeMembers(List.of(member));
    RealtimeEventDispatcher dispatcher =
        new RealtimeEventDispatcher(
            repository, registry, new ObjectMapper().findAndRegisterModules());

    dispatcher.dispatch(
        conversationId,
        new RealtimeEventEnvelope(
            UUID.randomUUID(),
            "message.created",
            Instant.parse("2026-08-22T12:00:00Z"),
            conversationId.value(),
            java.util.Map.of("sequence", 1)));

    assertEquals(1, memberFrames.size());
    assertEquals(memberFrames, secondDeviceFrames);
    assertEquals(0, outsiderFrames.size());
  }

  private static ConversationRepository activeMembers(List<UserId> members) {
    return (ConversationRepository)
        Proxy.newProxyInstance(
            ConversationRepository.class.getClassLoader(),
            new Class<?>[] {ConversationRepository.class},
            (proxy, method, arguments) ->
                method.getName().equals("findActiveMemberUserIds") ? members : null);
  }

  private static WebSocketConnection connection(String id, List<String> frames) {
    return (WebSocketConnection)
        Proxy.newProxyInstance(
            WebSocketConnection.class.getClassLoader(),
            new Class<?>[] {WebSocketConnection.class},
            (proxy, method, arguments) -> {
              return switch (method.getName()) {
                case "id" -> id;
                case "sendTextAndAwait" -> {
                  frames.add((String) arguments[0]);
                  yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
              };
            });
  }
}
