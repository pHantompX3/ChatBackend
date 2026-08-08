package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
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
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed runtime principal verification", exception);
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
