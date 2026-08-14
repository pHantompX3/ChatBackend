package com.wayden.messenger.session.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.application.PasswordHasher;
import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserStatus;
import com.wayden.messenger.session.domain.Session;
import com.wayden.messenger.session.domain.SessionId;
import com.wayden.messenger.session.domain.SessionStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class SessionServiceImpl implements SessionService {

  private static final Logger LOG = Logger.getLogger(SessionServiceImpl.class);
  private static final int TOKEN_BYTES = 32;

  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final Clock clock;
  private final RequestAuditContext requestAuditContext;
  private final AuthenticationRateLimitRepository rateLimitRepository;
  private final boolean rateLimitEnabled;
  private final int accountAttemptLimit;
  private final Duration accountWindow;
  private final int sourceAttemptLimit;
  private final Duration sourceWindow;
  private final SecureRandom random = new SecureRandom();

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "RequestAuditContext is CDI-managed request-scoped state intentionally shared within request handling.")
  public SessionServiceImpl(
      SessionRepository sessionRepository,
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      Clock clock,
      RequestAuditContext requestAuditContext,
      AuthenticationRateLimitRepository rateLimitRepository,
      @ConfigProperty(name = "chat.auth.rate-limit.enabled", defaultValue = "true")
          boolean rateLimitEnabled,
      @ConfigProperty(name = "chat.auth.rate-limit.account-limit", defaultValue = "10")
          int accountAttemptLimit,
      @ConfigProperty(name = "chat.auth.rate-limit.account-window", defaultValue = "PT5M")
          Duration accountWindow,
      @ConfigProperty(name = "chat.auth.rate-limit.source-limit", defaultValue = "30")
          int sourceAttemptLimit,
      @ConfigProperty(name = "chat.auth.rate-limit.source-window", defaultValue = "PT1M")
          Duration sourceWindow) {
    this.sessionRepository = sessionRepository;
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
    this.requestAuditContext = requestAuditContext;
    this.rateLimitRepository = rateLimitRepository;
    this.rateLimitEnabled = rateLimitEnabled;
    this.accountAttemptLimit = accountAttemptLimit;
    this.accountWindow = accountWindow;
    this.sourceAttemptLimit = sourceAttemptLimit;
    this.sourceWindow = sourceWindow;
  }

  @Override
  public LoginResult login(LoginCommand command) {
    if (command == null || command.username() == null || command.password() == null) {
      throw new IllegalArgumentException("Login command must not be null");
    }

    var normalizedUsername = NormalizedUsername.fromRaw(command.username());
    reserveAuthenticationCapacity(normalizedUsername, command.sourceAddress());
    Optional<User> user = userRepository.findByNormalizedUsername(normalizedUsername);
    if (user.isEmpty() || !passwordHasher.verify(command.password(), user.get().passwordHash())) {
      throw new SessionExceptions.InvalidCredentialsException();
    }

    if (user.get().status() != UserStatus.ACTIVE) {
      throw new SessionExceptions.DisabledUserException();
    }

    String rawToken = generateRawToken();
    Session session =
        new Session(
            new SessionId(UUID.randomUUID()),
            user.get().id(),
            hashToken(rawToken),
            clock.instant(),
            clock.instant().plusSeconds(60 * 60 * 24),
            null,
            null,
            command.userAgent(),
            command.sourceAddress(),
            SessionStatus.ACTIVE);

    sessionRepository.save(session);

    requestAuditContext.putCustomAttribute("identityEvent", "session.created");
    requestAuditContext.putCustomAttribute("actorUserId", user.get().id().value().toString());
    requestAuditContext.putCustomAttribute("actorUsername", user.get().username());
    requestAuditContext.putCustomAttribute("actorAuthType", "credentials");
    requestAuditContext.putCustomAttribute("targetUserId", user.get().id().value().toString());
    requestAuditContext.putCustomAttribute("sessionId", session.id().value().toString());

    return new LoginResult(session.id().value().toString(), rawToken, user.get());
  }

  private void reserveAuthenticationCapacity(
      NormalizedUsername normalizedUsername, String sourceAddress) {
    if (!rateLimitEnabled) {
      return;
    }
    String source = sourceAddress == null || sourceAddress.isBlank() ? "unknown" : sourceAddress;
    AuthenticationRateLimitRepository.Decision decision =
        rateLimitRepository.reserve(
            hash(normalizedUsername.value()),
            hash(source),
            accountAttemptLimit,
            accountWindow,
            sourceAttemptLimit,
            sourceWindow);
    requestAuditContext.putCustomAttribute(
        "authenticationThrottleOutcome", decision.allowed() ? "allowed" : "rejected");
    if (!decision.allowed()) {
      requestAuditContext.putCustomAttribute(
          "authenticationThrottleScope", decision.exhaustedScope());
      requestAuditContext.putCustomAttribute(
          "authenticationThrottleRetryAfter", Long.toString(decision.retryAfterSeconds()));
      throw new SessionExceptions.RateLimitedException(decision.retryAfterSeconds());
    }
  }

  @Override
  public void logout(LogoutCommand command) {
    if (command == null || command.rawToken() == null || command.rawToken().isBlank()) {
      throw new IllegalArgumentException("Logout token must not be blank");
    }

    Session session = resolveActiveSession(command.rawToken());
    sessionRepository.revoke(session.id(), clock.instant());

    requestAuditContext.putCustomAttribute("identityEvent", "session.revoked");
    requestAuditContext.putCustomAttribute("actorUserId", session.userId().value().toString());
    requestAuditContext.putCustomAttribute("actorAuthType", "session");
    requestAuditContext.putCustomAttribute("targetSessionId", session.id().value().toString());
  }

  @Override
  public int revokeAllSessionsForUser(RevokeAllSessionsCommand command) {
    if (command == null || command.targetUserId() == null) {
      throw new IllegalArgumentException("Target user must not be null");
    }
    if (userRepository.findById(command.targetUserId()).isEmpty()) {
      throw new SessionExceptions.SessionUserNotFoundException();
    }

    int revokedSessionCount =
        sessionRepository.revokeAllForUser(command.targetUserId(), clock.instant());

    requestAuditContext.putCustomAttribute("identityEvent", "session.revoked.all");
    requestAuditContext.putCustomAttribute(
        "targetUserId", command.targetUserId().value().toString());
    requestAuditContext.putCustomAttribute(
        "revokedSessionCount", Integer.toString(revokedSessionCount));
    return revokedSessionCount;
  }

  @Override
  public Session resolveActiveSession(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new SessionExceptions.MissingTokenException();
    }
    byte[] hash = hashToken(rawToken);
    Optional<Session> maybeSession = sessionRepository.findByTokenHash(hash);
    if (maybeSession.isEmpty()) {
      throw new SessionExceptions.InvalidSessionException();
    }
    Session session = maybeSession.get();

    if (session.status() != SessionStatus.ACTIVE) {
      throw sessionStatusException(session);
    }
    if (!session.expiresAt().isAfter(clock.instant())) {
      throw new SessionExceptions.ExpiredSessionException();
    }

    sessionRepository.touch(session.id(), clock.instant());
    return session;
  }

  @Override
  public User resolveAuthenticatedUser(String rawToken) {
    Session session = resolveActiveSession(rawToken);
    Optional<User> user = userRepository.findById(session.userId());
    if (user.isEmpty()) {
      throw new SessionExceptions.InvalidSessionException();
    }
    if (user.get().status() != UserStatus.ACTIVE) {
      throw new SessionExceptions.DisabledUserException();
    }
    return user.get();
  }

  private RuntimeException sessionStatusException(Session session) {
    if (session.status() == SessionStatus.REVOKED) {
      return new SessionExceptions.RevokedSessionException();
    }
    if (session.status() == SessionStatus.EXPIRED) {
      return new SessionExceptions.ExpiredSessionException();
    }
    return new SessionExceptions.InvalidSessionException();
  }

  private String generateRawToken() {
    byte[] value = new byte[TOKEN_BYTES];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private byte[] hashToken(String rawToken) {
    return hash(rawToken);
  }

  private byte[] hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }
}
