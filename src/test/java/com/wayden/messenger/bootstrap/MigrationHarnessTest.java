package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SuppressWarnings("resource")
final class MigrationHarnessTest {

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
  void shouldMigrateEmptySqlServerAndCreateLogicalSchemasAndHistory() {
    migrateMaster();
    migrateApplicationSchemas();

    try (var connection =
            TestSqlSupport.openConnection(
                SQL_SERVER.getHost(),
                SQL_SERVER.getMappedPort(1433),
                "wl_chat",
                "sa",
                DB_PASSWORD);
        var schemaStatement = connection.prepareStatement(TestSqlSupport.SCHEMA_COUNT_SQL);
        var tableStatement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM sys.tables t "
                    + "JOIN sys.schemas s ON s.schema_id = t.schema_id "
                    + "WHERE s.name IN ('identity', 'audit') "
                    + "AND t.name IN ('user_account', 'invitation', 'http_audit_event')");
        var historyStatement =
            connection.prepareStatement(TestSqlSupport.FLYWAY_HISTORY_SCHEMA_SQL)) {

      try (var schemaResult = schemaStatement.executeQuery()) {
        schemaResult.next();
        assertEquals(4, schemaResult.getInt(1));
      }

      try (var historyResult = historyStatement.executeQuery()) {
        historyResult.next();
        assertEquals("platform", historyResult.getString(1));
      }

      try (var tableResult = tableStatement.executeQuery()) {
        tableResult.next();
        assertEquals(3, tableResult.getInt(1));
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to validate migrated schema state", exception);
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
    TestSqlSupport.migrateApplicationSchemas(jdbcUrl, "sa", DB_PASSWORD, APP_LOGIN, APP_PASSWORD);
  }
}
