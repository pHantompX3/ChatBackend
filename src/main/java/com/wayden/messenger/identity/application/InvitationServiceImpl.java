package com.wayden.messenger.identity.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.domain.Invitation;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class InvitationServiceImpl implements InvitationService {

  private static final Logger LOG = Logger.getLogger(InvitationServiceImpl.class);

  private final InvitationRepository invitationRepository;
  private final UserRepository userRepository;
  private final InvitationTokenGenerator invitationTokenGenerator;
  private final InvitationTokenHasher invitationTokenHasher;
  private final PasswordHasher passwordHasher;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final RequestAuditContext requestAuditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "RequestAuditContext is CDI-managed request-scoped state intentionally shared within request handling.")
  public InvitationServiceImpl(
      InvitationRepository invitationRepository,
      UserRepository userRepository,
      InvitationTokenGenerator invitationTokenGenerator,
      InvitationTokenHasher invitationTokenHasher,
      PasswordHasher passwordHasher,
      IdGenerator idGenerator,
      Clock clock,
      RequestAuditContext requestAuditContext) {
    this.invitationRepository = invitationRepository;
    this.userRepository = userRepository;
    this.invitationTokenGenerator = invitationTokenGenerator;
    this.invitationTokenHasher = invitationTokenHasher;
    this.passwordHasher = passwordHasher;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.requestAuditContext = requestAuditContext;
  }

  @Override
  public CreateInvitationResult createInvitation(CreateInvitationCommand command) {
    requireActiveAdminActor(command.actorUserId());

    final String rawToken = invitationTokenGenerator.generateToken();
    final InvitationTokenHash tokenHash = invitationTokenHasher.hash(rawToken);
    final var now = clock.instant();

    final Invitation invitation =
        new Invitation(
            idGenerator.newInvitationId(),
            tokenHash,
            command.actorUserId(),
            command.expiresAt(),
            null,
            null,
            null,
            now);

    final Invitation saved = invitationRepository.save(invitation);
    requestAuditContext.putCustomAttribute("identityEvent", "invitation.created");
    requestAuditContext.putCustomAttribute("actorUserId", command.actorUserId().value().toString());
    requestAuditContext.putCustomAttribute("actorAuthType", "admin-session");
    requestAuditContext.putCustomAttribute("targetInvitationId", saved.id().value().toString());
    LOG.infof("invitation created requestId=%s", requestAuditContext.getRequestId());

    return new CreateInvitationResult(saved.id(), rawToken);
  }

  @Override
  public void revokeInvitation(RevokeInvitationCommand command) {
    requireActiveAdminActor(command.actorUserId());

    final boolean revoked =
        invitationRepository.markRevoked(
            command.invitationId(), command.actorUserId(), clock.instant());
    if (!revoked) {
      throw new IdentityExceptions.InvitationNotFoundException();
    }

    requestAuditContext.putCustomAttribute("identityEvent", "invitation.revoked");
    requestAuditContext.putCustomAttribute("actorUserId", command.actorUserId().value().toString());
    requestAuditContext.putCustomAttribute("actorAuthType", "admin-session");
    requestAuditContext.putCustomAttribute(
        "targetInvitationId", command.invitationId().value().toString());
  }

  @Override
  public RedeemInvitationResult redeemInvitation(RedeemInvitationCommand command) {
    final InvitationTokenHash tokenHash = invitationTokenHasher.hash(command.invitationToken());
    final Invitation invitation =
        invitationRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () -> {
                  auditRedeemFailure("INVITATION_NOT_FOUND", null);
                  return new IdentityExceptions.InvitationNotFoundException();
                });

    if (invitation.isRevoked()) {
      auditRedeemFailure("INVITATION_REVOKED", invitation);
      throw new IdentityExceptions.InvitationRevokedException();
    }
    if (invitation.isRedeemed()) {
      auditRedeemFailure("INVITATION_ALREADY_REDEEMED", invitation);
      throw new IdentityExceptions.InvitationAlreadyRedeemedException();
    }
    if (invitation.isExpired(clock)) {
      auditRedeemFailure("INVITATION_EXPIRED", invitation);
      throw new IdentityExceptions.InvitationExpiredException();
    }

    final NormalizedUsername normalizedUsername = NormalizedUsername.fromRaw(command.username());
    if (userRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
      auditRedeemFailure("DUPLICATE_USERNAME", invitation);
      throw new IdentityExceptions.DuplicateUsernameException(
          "Username is already in use: " + normalizedUsername.value());
    }

    final var now = clock.instant();
    final var newUserId = idGenerator.newUserId();
    final User user =
        new User(
            newUserId,
            command.username().trim(),
            normalizedUsername,
            passwordHasher.hash(command.password()),
            SystemRole.USER,
            UserStatus.ACTIVE,
            now,
            now);

    final User savedUser = userRepository.save(user);
    final boolean redeemed =
        invitationRepository.markRedeemed(invitation.id(), savedUser.id(), now);
    if (!redeemed) {
      auditRedeemFailure("INVITATION_ALREADY_REDEEMED", invitation);
      throw new IdentityExceptions.InvitationAlreadyRedeemedException();
    }

    requestAuditContext.putCustomAttribute("identityEvent", "invitation.redeemed");
    requestAuditContext.putCustomAttribute("actorUserId", savedUser.id().value().toString());
    requestAuditContext.putCustomAttribute("actorUsername", savedUser.username());
    requestAuditContext.putCustomAttribute("actorAuthType", "invitation");
    requestAuditContext.putCustomAttribute("targetUserId", savedUser.id().value().toString());
    requestAuditContext.putCustomAttribute(
        "targetInvitationId", invitation.id().value().toString());
    LOG.infof("invitation redeemed requestId=%s", requestAuditContext.getRequestId());

    return new RedeemInvitationResult(savedUser.id(), savedUser.username());
  }

  private void auditRedeemFailure(String code, Invitation invitation) {
    requestAuditContext.putCustomAttribute("identityEvent", "invitation.redeem.failed");
    requestAuditContext.putCustomAttribute("failureCode", code);
    if (invitation != null) {
      requestAuditContext.putCustomAttribute(
          "targetInvitationId", invitation.id().value().toString());
    }
  }

  private void requireActiveAdminActor(UserId actorUserId) {
    User actor =
        userRepository
            .findById(actorUserId)
            .orElseThrow(
                () -> {
                  requestAuditContext.putCustomAttribute(
                      "identityEvent", "invitation.actor.denied");
                  requestAuditContext.putCustomAttribute(
                      "failureCode", "INVITATION_ACTOR_FORBIDDEN");
                  requestAuditContext.putCustomAttribute(
                      "actorUserId", actorUserId.value().toString());
                  requestAuditContext.putCustomAttribute("actorAuthType", "admin-session");
                  requestAuditContext.putCustomAttribute(
                      "targetUserId", actorUserId.value().toString());
                  return new IdentityExceptions.ActorNotAuthorizedException();
                });

    if (actor.systemRole() != SystemRole.ADMIN || actor.status() != UserStatus.ACTIVE) {
      requestAuditContext.putCustomAttribute("identityEvent", "invitation.actor.denied");
      requestAuditContext.putCustomAttribute("failureCode", "INVITATION_ACTOR_FORBIDDEN");
      requestAuditContext.putCustomAttribute("actorUserId", actor.id().value().toString());
      requestAuditContext.putCustomAttribute("actorUsername", actor.username());
      requestAuditContext.putCustomAttribute("actorAuthType", "admin-session");
      requestAuditContext.putCustomAttribute("targetUserId", actor.id().value().toString());
      throw new IdentityExceptions.ActorNotAuthorizedException();
    }

    requestAuditContext.putCustomAttribute("actorUserId", actor.id().value().toString());
    requestAuditContext.putCustomAttribute("actorUsername", actor.username());
    requestAuditContext.putCustomAttribute("actorAuthType", "admin-session");
  }
}
