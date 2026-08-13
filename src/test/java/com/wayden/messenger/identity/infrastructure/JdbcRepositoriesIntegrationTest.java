package com.wayden.messenger.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.wayden.messenger.identity.application.IdentityExceptions;
import com.wayden.messenger.identity.domain.Invitation;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.PasswordHash;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SuppressWarnings("resource")
final class JdbcRepositoriesIntegrationTest {

  private static final String DB_PASSWORD = "StrongPassw0rd!";
  private static final String APP_LOGIN = "wl_chat_app";
  private static final String APP_PASSWORD = "AppPassw0rd!";

  @Container
  static final MSSQLServerContainer<?> SQL_SERVER =
      new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
          .acceptLicense()
          .withPassword(DB_PASSWORD);

  private static DataSource dataSource;
  private static DataSource cleanupDataSource;

  private JdbcUserRepository userRepository;
  private JdbcInvitationRepository invitationRepository;

  @BeforeAll
  static void setUpDatabase() {
    migrateMaster();
    migrateApplicationSchemas();

    SQLServerDataSource sqlServerDataSource = new SQLServerDataSource();
    sqlServerDataSource.setURL(
        jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "wl_chat"));
    sqlServerDataSource.setUser(APP_LOGIN);
    sqlServerDataSource.setPassword(APP_PASSWORD);
    dataSource = sqlServerDataSource;

    SQLServerDataSource adminDataSource = new SQLServerDataSource();
    adminDataSource.setURL(
        jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "wl_chat"));
    adminDataSource.setUser("sa");
    adminDataSource.setPassword(DB_PASSWORD);
    cleanupDataSource = adminDataSource;
  }

  @BeforeEach
  void setUpRepositories() {
    clearIdentityTables();
    userRepository = new JdbcUserRepository(dataSource);
    invitationRepository = new JdbcInvitationRepository(dataSource);
  }

  @Test
  void userRepositoryShouldPersistFindAndDetectAnyUser() {
    assertFalse(userRepository.existsAnyUser());

    User saved = userRepository.save(newUser("Admin One"));

    assertTrue(userRepository.existsAnyUser());
    var found = userRepository.findByNormalizedUsername(new NormalizedUsername("admin one"));
    assertTrue(found.isPresent());

    User loaded = found.orElseThrow();
    assertEquals(saved.id(), loaded.id());
    assertEquals("Admin One", loaded.username());
    assertEquals(new NormalizedUsername("admin one"), loaded.normalizedUsername());
    assertEquals(SystemRole.ADMIN, loaded.systemRole());
    assertEquals(UserStatus.ACTIVE, loaded.status());
    assertNotNull(loaded.createdAt());
    assertNotNull(loaded.updatedAt());
  }

  @Test
  void userRepositoryShouldTranslateDuplicateNormalizedUsername() {
    userRepository.save(newUser("Duplicate User"));

    assertThrows(
        IdentityExceptions.DuplicateUsernameException.class,
        () -> userRepository.save(newUser("duplicate user")));
  }

  @Test
  void invitationRepositoryShouldPersistFindAndTransitionToRevoked() {
    User creator = userRepository.save(newUser("Creator User"));
    Invitation invitation =
        new Invitation(
            InvitationId.newId(),
            new InvitationTokenHash(tokenBytes(1)),
            creator.id(),
            Instant.now().plus(2, ChronoUnit.DAYS),
            null,
            null,
            null,
            Instant.now());

    invitationRepository.save(invitation);

    var found = invitationRepository.findByTokenHash(new InvitationTokenHash(tokenBytes(1)));
    assertTrue(found.isPresent());
    Invitation loaded = found.orElseThrow();
    assertEquals(invitation.id(), loaded.id());
    assertArrayEquals(tokenBytes(1), loaded.tokenHash().value());

    Instant revokedAt = Instant.now();
    assertTrue(invitationRepository.markRevoked(invitation.id(), creator.id(), revokedAt));
    assertFalse(invitationRepository.markRevoked(invitation.id(), creator.id(), revokedAt));
    assertFalse(invitationRepository.markRedeemed(invitation.id(), creator.id(), Instant.now()));
  }

  @Test
  void invitationRepositoryShouldTransitionToRedeemedOnlyOnce() {
    User creator = userRepository.save(newUser("Creator Two"));
    User redeemer = userRepository.save(newUser("Redeemer User"));

    Invitation invitation =
        new Invitation(
            InvitationId.newId(),
            new InvitationTokenHash(tokenBytes(2)),
            creator.id(),
            Instant.now().plus(1, ChronoUnit.DAYS),
            null,
            null,
            null,
            Instant.now());

    invitationRepository.save(invitation);

    Instant redeemedAt = Instant.now();
    assertTrue(invitationRepository.markRedeemed(invitation.id(), redeemer.id(), redeemedAt));
    assertFalse(invitationRepository.markRedeemed(invitation.id(), redeemer.id(), redeemedAt));
    assertFalse(invitationRepository.markRevoked(invitation.id(), creator.id(), Instant.now()));

    Invitation loaded =
        invitationRepository.findByTokenHash(new InvitationTokenHash(tokenBytes(2))).orElseThrow();
    assertEquals(redeemer.id(), loaded.redeemedBy());
    assertNotNull(loaded.redeemedAt());
  }

  private static User newUser(String username) {
    Instant now = Instant.now();
    return new User(
        UserId.newId(),
        username,
        NormalizedUsername.fromRaw(username),
        new PasswordHash("$argon2id$dummy"),
        SystemRole.ADMIN,
        UserStatus.ACTIVE,
        now,
        now);
  }

  private static byte[] tokenBytes(int seed) {
    byte[] bytes = new byte[32];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) (seed + i);
    }
    return bytes;
  }

  private static String jdbcUrl(String host, int port, String databaseName) {
    return "jdbc:sqlserver://"
        + host
        + ":"
        + port
        + ";databaseName="
        + databaseName
        + ";encrypt=true;trustServerCertificate=true";
  }

  private static void migrateMaster() {
    String jdbcUrl = jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "master");
    String location =
        Path.of("scripts", "database", "flyway", "master").toAbsolutePath().toString();

    Flyway.configure()
        .dataSource(jdbcUrl, "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .load()
        .migrate();
  }

  private static void migrateApplicationSchemas() {
    String jdbcUrl = jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "wl_chat");
    String location =
        Path.of("scripts", "database", "flyway", "wl_chat").toAbsolutePath().toString();

    Flyway.configure()
        .dataSource(jdbcUrl, "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .defaultSchema("platform")
        .schemas("platform", "identity", "messaging", "audit")
        .table("flyway_schema_history")
        .placeholders(java.util.Map.of("app_login", APP_LOGIN, "app_password", APP_PASSWORD))
        .load()
        .migrate();
  }

  private static void clearIdentityTables() {
    try (var connection = cleanupDataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [messaging].[direct_conversation_pair]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation_member]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to clear identity test tables", e);
    }
  }
}
