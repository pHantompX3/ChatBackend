package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SuppressWarnings("resource")
final class SchemaVerificationTest {

  private static final String DB_PASSWORD = "StrongPassw0rd!";
  private static final String APP_LOGIN = "wl_chat_app";
  private static final String APP_PASSWORD = "AppPassw0rd!";

  static {
    Logger.getLogger("com.microsoft.sqlserver.jdbc.internals.SQLServerConnection")
        .setLevel(Level.SEVERE);
  }

  @Container
  static final MSSQLServerContainer<?> SQL_SERVER =
      new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
          .acceptLicense()
          .withPassword(DB_PASSWORD);

  @Test
  void runtimePrincipalShouldConnectButNotPerformDdl() {
    migrateMaster();
    migrateApplicationSchemas();

    try (var connection =
            TestSqlSupport.openConnection(
                SQL_SERVER.getHost(),
                SQL_SERVER.getMappedPort(1433),
                "wl_chat",
                APP_LOGIN,
                APP_PASSWORD);
        var dbStatement = connection.prepareStatement("SELECT DB_NAME()")) {

      try (var dbResult = dbStatement.executeQuery()) {
        dbResult.next();
        assertEquals("wl_chat", dbResult.getString(1));
      }

      final SQLException sqlException =
          org.junit.jupiter.api.Assertions.assertThrows(
              SQLException.class,
              () -> {
                try (var ddlStatement = connection.createStatement()) {
                  ddlStatement.executeUpdate(
                      "CREATE TABLE dbo.__forbidden_runtime (id INT NOT NULL)");
                }
              });

      assertTrue(sqlException.getMessage().toLowerCase().contains("permission"));

      final SQLException updateDenied =
          org.junit.jupiter.api.Assertions.assertThrows(
              SQLException.class,
              () -> {
                try (var updateStatement = connection.createStatement()) {
                  updateStatement.executeUpdate(
                      "UPDATE audit.http_audit_event SET event_type = 'forbidden' WHERE 1 = 0");
                }
              });

      assertTrue(updateDenied.getMessage().toLowerCase().contains("permission"));

      assertEquals(1, permission(connection, "SELECT"));
      assertEquals(1, permission(connection, "INSERT"));
      assertEquals(1, permission(connection, "UPDATE"));
      assertEquals(0, permission(connection, "DELETE"));

      final SQLException deleteDenied =
          org.junit.jupiter.api.Assertions.assertThrows(
              SQLException.class,
              () -> {
                try (var deleteStatement = connection.createStatement()) {
                  deleteStatement.executeUpdate("DELETE FROM messaging.message WHERE 1 = 0");
                }
              });
      assertTrue(deleteDenied.getMessage().toLowerCase().contains("permission"));
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed runtime principal verification", exception);
    }

    verifyMessageSchema();
  }

  @Test
  void failedMessageInsertShouldRollbackSequenceAllocation() {
    migrateMaster();
    migrateApplicationSchemas();

    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    seedMessageOwner(userId, conversationId);

    try (var connection =
        TestSqlSupport.openConnection(
            SQL_SERVER.getHost(),
            SQL_SERVER.getMappedPort(1433),
            "wl_chat",
            APP_LOGIN,
            APP_PASSWORD)) {
      connection.setAutoCommit(false);
      try (var sequence =
              connection.prepareStatement(
                  "UPDATE messaging.conversation "
                      + "SET next_message_sequence = next_message_sequence + 1, updated_at = ? "
                      + "OUTPUT INSERTED.next_message_sequence - 1 WHERE id = ?");
          var invalidInsert =
              connection.prepareStatement(
                  "INSERT INTO messaging.message "
                      + "(id, conversation_id, sender_id, client_message_id, sequence_number, "
                      + "message_type, body, created_at) VALUES (?, ?, ?, ?, ?, 'TEXT', NULL, ?)")) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        sequence.setObject(1, now);
        sequence.setObject(2, conversationId);
        try (var result = sequence.executeQuery()) {
          result.next();
          assertEquals(1, result.getLong(1));
        }
        invalidInsert.setObject(1, UUID.randomUUID());
        invalidInsert.setObject(2, conversationId);
        invalidInsert.setObject(3, userId);
        invalidInsert.setObject(4, UUID.randomUUID());
        invalidInsert.setLong(5, 1);
        invalidInsert.setObject(6, now);
        org.junit.jupiter.api.Assertions.assertThrows(
            SQLException.class, invalidInsert::executeUpdate);
        connection.rollback();
      }

      try (var statement =
          connection.prepareStatement(
              "SELECT next_message_sequence, "
                  + "(SELECT COUNT(*) FROM messaging.message WHERE conversation_id = ?) "
                  + "AS message_count FROM messaging.conversation WHERE id = ?")) {
        statement.setObject(1, conversationId);
        statement.setObject(2, conversationId);
        try (var result = statement.executeQuery()) {
          result.next();
          assertEquals(1, result.getLong("next_message_sequence"));
          assertEquals(0, result.getLong("message_count"));
        }
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Failed message transaction rollback verification", exception);
    }
  }

  private static int permission(java.sql.Connection connection, String permission)
      throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT HAS_PERMS_BY_NAME('messaging.message', 'OBJECT', ?)")) {
      statement.setString(1, permission);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private static void verifyMessageSchema() {
    try (var connection =
            TestSqlSupport.openConnection(
                SQL_SERVER.getHost(),
                SQL_SERVER.getMappedPort(1433),
                "wl_chat",
                "sa",
                DB_PASSWORD);
        var statement = connection.createStatement()) {
      try (var result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM sys.columns "
                  + "WHERE object_id = OBJECT_ID('messaging.message')")) {
        result.next();
        assertEquals(10, result.getInt(1));
      }
      try (var result =
          statement.executeQuery(
              "SELECT name, type_desc, is_unique, is_primary_key "
                  + "FROM sys.indexes WHERE object_id = OBJECT_ID('messaging.message')")) {
        boolean clusteredSequence = false;
        boolean nonclusteredPrimaryKey = false;
        while (result.next()) {
          if ("uq_messaging_message_sequence".equals(result.getString("name"))) {
            clusteredSequence =
                "CLUSTERED".equals(result.getString("type_desc")) && result.getBoolean("is_unique");
          }
          if ("pk_messaging_message".equals(result.getString("name"))) {
            nonclusteredPrimaryKey =
                "NONCLUSTERED".equals(result.getString("type_desc"))
                    && result.getBoolean("is_primary_key");
          }
        }
        assertTrue(clusteredSequence);
        assertTrue(nonclusteredPrimaryKey);
      }
      try (var result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM sys.objects WHERE parent_object_id = "
                  + "OBJECT_ID('messaging.message') AND name IN ("
                  + "'pk_messaging_message', 'fk_messaging_message_conversation', "
                  + "'fk_messaging_message_sender_membership', "
                  + "'uq_messaging_message_client_id', 'uq_messaging_message_sequence', "
                  + "'ck_messaging_message_sequence_positive', 'ck_messaging_message_type', "
                  + "'ck_messaging_message_body', 'ck_messaging_message_timestamps')")) {
        result.next();
        assertEquals(9, result.getInt(1));
      }
      assertFalse(connection.isClosed());
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed message schema verification", exception);
    }
  }

  private static void seedMessageOwner(UUID userId, UUID conversationId) {
    try (var connection =
        TestSqlSupport.openConnection(
            SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "wl_chat", "sa", DB_PASSWORD)) {
      LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
      try (var user =
          connection.prepareStatement(
              "INSERT INTO identity.user_account "
                  + "(id, username, normalized_username, password_hash, system_role, status, "
                  + "created_at, updated_at) VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?)")) {
        String username = "schema-message-" + userId;
        user.setObject(1, userId);
        user.setString(2, username);
        user.setString(3, username);
        user.setString(4, "test-password-hash");
        user.setObject(5, now);
        user.setObject(6, now);
        user.executeUpdate();
      }
      try (var conversation =
          connection.prepareStatement(
              "INSERT INTO messaging.conversation "
                  + "(id, conversation_type, title, created_by, next_message_sequence, "
                  + "created_at, updated_at) VALUES (?, 'GROUP', ?, ?, 1, ?, ?)")) {
        conversation.setObject(1, conversationId);
        conversation.setString(2, "Schema message transaction");
        conversation.setObject(3, userId);
        conversation.setObject(4, now);
        conversation.setObject(5, now);
        conversation.executeUpdate();
      }
      try (var membership =
          connection.prepareStatement(
              "INSERT INTO messaging.conversation_member "
                  + "(conversation_id, user_id, conversation_role, joined_at, "
                  + "last_delivered_sequence, last_read_sequence) VALUES (?, ?, 'OWNER', ?, 0, 0)")) {
        membership.setObject(1, conversationId);
        membership.setObject(2, userId);
        membership.setObject(3, now);
        membership.executeUpdate();
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to seed message transaction verification", exception);
    }
  }

  private static void migrateMaster() {
    final String jdbcUrl =
        TestSqlSupport.jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "master");
    final String location =
        Path.of("scripts", "database", "flyway", "master").toAbsolutePath().toString();

    Flyway.configure()
        .dataSource(jdbcUrl, "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .load()
        .migrate();
  }

  private static void migrateApplicationSchemas() {
    final String jdbcUrl =
        TestSqlSupport.jdbcUrl(SQL_SERVER.getHost(), SQL_SERVER.getMappedPort(1433), "wl_chat");
    final String location =
        Path.of("scripts", "database", "flyway", "wl_chat").toAbsolutePath().toString();

    Flyway.configure()
        .dataSource(jdbcUrl, "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .defaultSchema("platform")
        .schemas("platform", "identity", "messaging", "audit")
        .table("flyway_schema_history")
        .placeholders(TestSqlSupport.placeholders(APP_LOGIN, APP_PASSWORD))
        .load()
        .migrate();
  }
}
