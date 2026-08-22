package com.wayden.messenger.realtime.application;

import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import com.wayden.messenger.session.application.SessionRepository;
import com.wayden.messenger.session.domain.Session;
import com.wayden.messenger.session.domain.SessionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Optional;

/**
 * Validates a raw token supplied during WebSocket handshake by SHA-256 hashing it and looking up
 * the corresponding active session. Mirrors the same token resolution logic used by the HTTP layer
 * but adapted for stateless handshake authentication.
 */
@ApplicationScoped
public class WebSocketSessionAuthenticator {

  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  @Inject
  public WebSocketSessionAuthenticator(
      SessionRepository sessionRepository, UserRepository userRepository, Clock clock) {
    this.sessionRepository = sessionRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * Attempts to authenticate the raw token from the handshake.
   *
   * @param rawToken the raw bearer token string
   * @return an authenticated {@link AuthenticatedPrincipal} if valid, or {@link Optional#empty()}
   *     if missing, not found, revoked, or expired
   */
  public Optional<AuthenticatedPrincipal> authenticate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    byte[] hash = sha256(rawToken);
    Optional<Session> maybeSession = sessionRepository.findByTokenHash(hash);
    if (maybeSession.isEmpty()) {
      return Optional.empty();
    }
    Session session = maybeSession.get();
    if (session.status() != SessionStatus.ACTIVE) {
      return Optional.empty();
    }
    if (!session.expiresAt().isAfter(clock.instant())) {
      return Optional.empty();
    }
    if (userRepository
        .findById(session.userId())
        .filter(user -> user.status() == UserStatus.ACTIVE)
        .isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new AuthenticatedPrincipal(session.userId(), session.id(), session.expiresAt()));
  }

  private static byte[] sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public record AuthenticatedPrincipal(
      UserId userId,
      com.wayden.messenger.session.domain.SessionId sessionId,
      java.time.Instant expiresAt) {}
}
