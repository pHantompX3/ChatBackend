package com.wayden.messenger.identity.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class AdminServiceImpl implements AdminService {

  private static final Logger LOG = Logger.getLogger(AdminServiceImpl.class);

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final RequestAuditContext requestAuditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "RequestAuditContext is CDI-managed request-scoped state intentionally shared within request handling.")
  public AdminServiceImpl(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      IdGenerator idGenerator,
      Clock clock,
      RequestAuditContext requestAuditContext) {
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.requestAuditContext = requestAuditContext;
  }

  @Override
  public BootstrapAdminResult bootstrapFirstAdmin(BootstrapAdminCommand command) {
    if (userRepository.existsAnyUser()) {
      throw new IdentityExceptions.BootstrapAlreadyCompletedException();
    }

    final NormalizedUsername normalizedUsername = NormalizedUsername.fromRaw(command.username());
    if (userRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
      throw new IdentityExceptions.DuplicateUsernameException(
          "Username is already in use: " + normalizedUsername.value());
    }

    final var now = clock.instant();
    final User user =
        new User(
            idGenerator.newUserId(),
            command.username().trim(),
            normalizedUsername,
            passwordHasher.hash(command.password()),
            SystemRole.ADMIN,
            UserStatus.ACTIVE,
            now,
            now);

    final User saved = userRepository.save(user);
    requestAuditContext.putCustomAttribute("identityEvent", "admin.bootstrap.created");
    requestAuditContext.putCustomAttribute("actorUserId", saved.id().value().toString());
    requestAuditContext.putCustomAttribute("actorUsername", saved.username());
    requestAuditContext.putCustomAttribute("actorAuthType", "bootstrap");
    requestAuditContext.putCustomAttribute("targetUserId", saved.id().value().toString());
    LOG.infof("bootstrap admin created requestId=%s", requestAuditContext.getRequestId());

    return new BootstrapAdminResult(saved.id(), saved.username());
  }
}
