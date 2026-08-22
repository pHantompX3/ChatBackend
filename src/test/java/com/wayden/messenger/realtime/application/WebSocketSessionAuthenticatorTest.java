package com.wayden.messenger.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.PasswordHash;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import com.wayden.messenger.session.application.SessionRepository;
import com.wayden.messenger.session.domain.Session;
import com.wayden.messenger.session.domain.SessionId;
import com.wayden.messenger.session.domain.SessionStatus;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class WebSocketSessionAuthenticatorTest {

  private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
  private static final UserId USER_ID = new UserId(UUID.randomUUID());

  @Test
  void shouldAuthenticateOnlyActiveUnexpiredSessionsForActiveUsers() {
    Session activeSession = session(SessionStatus.ACTIVE, NOW.plusSeconds(60));
    var authenticator = authenticator(activeSession, UserStatus.ACTIVE);

    var principal = authenticator.authenticate("valid-token").orElseThrow();

    assertEquals(USER_ID, principal.userId());
    assertEquals(activeSession.id(), principal.sessionId());
    assertTrue(authenticator(activeSession, UserStatus.DISABLED).authenticate("token").isEmpty());
    assertTrue(
        authenticator(session(SessionStatus.REVOKED, NOW.plusSeconds(60)), UserStatus.ACTIVE)
            .authenticate("token")
            .isEmpty());
    assertTrue(
        authenticator(session(SessionStatus.ACTIVE, NOW), UserStatus.ACTIVE)
            .authenticate("token")
            .isEmpty());
  }

  private static WebSocketSessionAuthenticator authenticator(
      Session session, UserStatus userStatus) {
    SessionRepository sessions =
        proxy(
            SessionRepository.class,
            (method, arguments) ->
                method.equals("findByTokenHash") ? Optional.of(session) : defaultValue(method));
    UserRepository users =
        proxy(
            UserRepository.class,
            (method, arguments) ->
                method.equals("findById") ? Optional.of(user(userStatus)) : defaultValue(method));
    return new WebSocketSessionAuthenticator(sessions, users, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Session session(SessionStatus status, Instant expiresAt) {
    return new Session(
        new SessionId(UUID.randomUUID()),
        USER_ID,
        new byte[] {1},
        NOW.minusSeconds(60),
        expiresAt,
        NOW,
        status == SessionStatus.REVOKED ? NOW : null,
        null,
        null,
        status);
  }

  private static User user(UserStatus status) {
    return new User(
        USER_ID,
        "member",
        new NormalizedUsername("member"),
        new PasswordHash("hash"),
        SystemRole.USER,
        status,
        NOW,
        NOW);
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Invocation invocation) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, arguments) -> invocation.invoke(method.getName(), arguments));
  }

  private static Object defaultValue(String method) {
    return method.startsWith("find") ? Optional.empty() : null;
  }

  @FunctionalInterface
  private interface Invocation {
    Object invoke(String method, Object[] arguments);
  }
}
