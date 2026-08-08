package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class MigrationHarnessTest {

  private static final String DB_PASSWORD = "StrongPassw0rd!";
  private static final String APP_LOGIN = "wl_chat_app";
  private static final String APP_PASSWORD = "AppPassw0rd!";

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
