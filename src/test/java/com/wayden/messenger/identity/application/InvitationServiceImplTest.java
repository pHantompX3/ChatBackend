package com.wayden.messenger.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.domain.Invitation;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.PasswordHash;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InvitationServiceImplTest {

  private static final UserId DEFAULT_ACTOR_ID = UserId.newId();

  @Test
  void createInvitationShouldRejectUnknownActor() {
    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.empty(), true),
            new StubUserRepository(Optional.empty(), Optional.empty()),
            () -> "generated-token",
            rawToken -> new InvitationTokenHash(new byte[] {9}),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.ActorNotAuthorizedException.class,
        () ->
            service.createInvitation(
                new CreateInvitationCommand(
                    DEFAULT_ACTOR_ID, Instant.parse("2026-08-08T10:10:00Z"))));

    assertEquals(
        "invitation.actor.denied", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals(
        "INVITATION_ACTOR_FORBIDDEN", auditContext.getCustomAttributes().get("failureCode"));
  }

  @Test
  void createInvitationShouldRejectNonAdminActor() {
    User nonAdminActor =
        new User(
            DEFAULT_ACTOR_ID,
            "member-user",
            NormalizedUsername.fromRaw("member-user"),
            new PasswordHash("hash"),
            SystemRole.USER,
            UserStatus.ACTIVE,
            Instant.parse("2026-08-08T09:00:00Z"),
            Instant.parse("2026-08-08T09:00:00Z"));

    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.empty(), true),
            new StubUserRepository(Optional.empty(), Optional.of(nonAdminActor)),
            () -> "generated-token",
            rawToken -> new InvitationTokenHash(new byte[] {9}),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.ActorNotAuthorizedException.class,
        () ->
            service.createInvitation(
                new CreateInvitationCommand(
                    nonAdminActor.id(), Instant.parse("2026-08-08T10:10:00Z"))));

    assertEquals(
        "invitation.actor.denied", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals(
        "INVITATION_ACTOR_FORBIDDEN", auditContext.getCustomAttributes().get("failureCode"));
  }

  @Test
  void revokeInvitationShouldRejectUnknownActor() {
    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.empty(), true),
            new StubUserRepository(Optional.empty(), Optional.empty()),
            () -> "generated-token",
            rawToken -> new InvitationTokenHash(new byte[] {9}),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.ActorNotAuthorizedException.class,
        () ->
            service.revokeInvitation(
                new RevokeInvitationCommand(InvitationId.newId(), DEFAULT_ACTOR_ID)));

    assertEquals(
        "invitation.actor.denied", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals(
        "INVITATION_ACTOR_FORBIDDEN", auditContext.getCustomAttributes().get("failureCode"));
  }

  @Test
  void revokeInvitationShouldRejectNonAdminActor() {
    User nonAdminActor =
        new User(
            DEFAULT_ACTOR_ID,
            "member-user",
            NormalizedUsername.fromRaw("member-user"),
            new PasswordHash("hash"),
            SystemRole.USER,
            UserStatus.ACTIVE,
            Instant.parse("2026-08-08T09:00:00Z"),
            Instant.parse("2026-08-08T09:00:00Z"));

    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.empty(), true),
            new StubUserRepository(Optional.empty(), Optional.of(nonAdminActor)),
            () -> "generated-token",
            rawToken -> new InvitationTokenHash(new byte[] {9}),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.ActorNotAuthorizedException.class,
        () ->
            service.revokeInvitation(
                new RevokeInvitationCommand(InvitationId.newId(), nonAdminActor.id())));

    assertEquals(
        "invitation.actor.denied", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals(
        "INVITATION_ACTOR_FORBIDDEN", auditContext.getCustomAttributes().get("failureCode"));
  }

  @Test
  void redeemNotFoundShouldRecordFailureAuditMetadata() {
    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.empty(), true),
            new StubUserRepository(Optional.empty(), Optional.of(adminActor())),
            () -> "unused-token",
            rawToken -> new InvitationTokenHash(new byte[] {1}),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.InvitationNotFoundException.class,
        () -> service.redeemInvitation(new RedeemInvitationCommand("token", "user-a", "pw")));

    assertEquals(
        "invitation.redeem.failed", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals("INVITATION_NOT_FOUND", auditContext.getCustomAttributes().get("failureCode"));
  }

  @Test
  void redeemExpiredShouldRecordFailureAuditMetadataWithTarget() {
    Invitation expiredInvitation =
        new Invitation(
            InvitationId.newId(),
            new InvitationTokenHash(new byte[] {2}),
            UserId.newId(),
            Instant.parse("2026-08-08T09:59:00Z"),
            null,
            null,
            null,
            Instant.parse("2026-08-08T09:00:00Z"));

    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.of(expiredInvitation), true),
            new StubUserRepository(Optional.empty(), Optional.of(adminActor())),
            () -> "unused-token",
            rawToken -> expiredInvitation.tokenHash(),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.InvitationExpiredException.class,
        () -> service.redeemInvitation(new RedeemInvitationCommand("token", "user-b", "pw")));

    assertEquals(
        "invitation.redeem.failed", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals("INVITATION_EXPIRED", auditContext.getCustomAttributes().get("failureCode"));
    assertEquals(
        expiredInvitation.id().value().toString(),
        auditContext.getCustomAttributes().get("targetInvitationId"));
  }

  @Test
  void redeemDuplicateUsernameShouldRecordFailureAuditMetadata() {
    Invitation invitation =
        new Invitation(
            InvitationId.newId(),
            new InvitationTokenHash(new byte[] {3}),
            UserId.newId(),
            Instant.parse("2026-08-09T09:59:00Z"),
            null,
            null,
            null,
            Instant.parse("2026-08-08T09:00:00Z"));

    User existingUser =
        new User(
            UserId.newId(),
            "existing-user",
            NormalizedUsername.fromRaw("existing-user"),
            new PasswordHash("hash"),
            SystemRole.USER,
            UserStatus.ACTIVE,
            Instant.parse("2026-08-08T09:00:00Z"),
            Instant.parse("2026-08-08T09:00:00Z"));

    RequestAuditContext auditContext = new RequestAuditContext();
    InvitationServiceImpl service =
        new InvitationServiceImpl(
            new StubInvitationRepository(Optional.of(invitation), true),
            new StubUserRepository(Optional.of(existingUser), Optional.of(adminActor())),
            () -> "unused-token",
            rawToken -> invitation.tokenHash(),
            new StubPasswordHasher(),
            new StubIdGenerator(),
            fixedClock(),
            auditContext);

    assertThrows(
        IdentityExceptions.DuplicateUsernameException.class,
        () ->
            service.redeemInvitation(new RedeemInvitationCommand("token", "existing-user", "pw")));

    assertEquals(
        "invitation.redeem.failed", auditContext.getCustomAttributes().get("identityEvent"));
    assertEquals("DUPLICATE_USERNAME", auditContext.getCustomAttributes().get("failureCode"));
    assertEquals(
        invitation.id().value().toString(),
        auditContext.getCustomAttributes().get("targetInvitationId"));
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC);
  }

  private static User adminActor() {
    return new User(
        DEFAULT_ACTOR_ID,
        "admin-user",
        NormalizedUsername.fromRaw("admin-user"),
        new PasswordHash("hash"),
        SystemRole.ADMIN,
        UserStatus.ACTIVE,
        Instant.parse("2026-08-08T09:00:00Z"),
        Instant.parse("2026-08-08T09:00:00Z"));
  }

  private static final class StubInvitationRepository implements InvitationRepository {
    private final Optional<Invitation> invitation;
    private final boolean markRedeemedResult;

    private StubInvitationRepository(Optional<Invitation> invitation, boolean markRedeemedResult) {
      this.invitation = invitation;
      this.markRedeemedResult = markRedeemedResult;
    }

    @Override
    public Invitation save(Invitation invitation) {
      return invitation;
    }

    @Override
    public Optional<Invitation> findByTokenHash(InvitationTokenHash tokenHash) {
      return invitation;
    }

    @Override
    public boolean markRevoked(InvitationId invitationId, UserId actorUserId, Instant revokedAt) {
      return false;
    }

    @Override
    public boolean markRedeemed(InvitationId invitationId, UserId actorUserId, Instant redeemedAt) {
      return markRedeemedResult;
    }
  }

  private static final class StubUserRepository implements UserRepository {
    private final Optional<User> foundUser;
    private final Optional<User> foundById;

    private StubUserRepository(Optional<User> foundUser, Optional<User> foundById) {
      this.foundUser = foundUser;
      this.foundById = foundById;
    }

    @Override
    public boolean existsAnyUser() {
      return false;
    }

    @Override
    public Optional<User> findById(UserId userId) {
      return foundById;
    }

    @Override
    public Optional<User> findByNormalizedUsername(NormalizedUsername normalizedUsername) {
      return foundUser;
    }

    @Override
    public User save(User user) {
      return user;
    }

    @Override
    public User saveFirstAdminIfAbsent(User user) {
      return user;
    }
  }

  private static final class StubPasswordHasher implements PasswordHasher {
    @Override
    public PasswordHash hash(String rawPassword) {
      return new PasswordHash("hashed-value");
    }

    @Override
    public boolean verify(String rawPassword, PasswordHash passwordHash) {
      return true;
    }
  }

  private static final class StubIdGenerator implements IdGenerator {
    @Override
    public UserId newUserId() {
      return new UserId(UUID.randomUUID());
    }

    @Override
    public InvitationId newInvitationId() {
      return new InvitationId(UUID.randomUUID());
    }
  }
}
