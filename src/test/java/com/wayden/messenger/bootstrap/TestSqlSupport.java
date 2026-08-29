package com.wayden.messenger.bootstrap;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

public final class TestSqlSupport {

  static {
    quietSqlServerDriverLogs();
  }

  static final String SCHEMA_COUNT_SQL =
      "SELECT COUNT(*) FROM sys.schemas WHERE name IN ('platform','identity','messaging','audit')";

  static final String FLYWAY_HISTORY_SCHEMA_SQL =
      "SELECT TOP 1 s.name FROM sys.tables t "
          + "JOIN sys.schemas s ON s.schema_id = t.schema_id "
          + "WHERE t.name = 'flyway_schema_history'";

  private TestSqlSupport() {}

  static String jdbcUrl(final String host, final int port, final String databaseName) {
    return "jdbc:sqlserver://"
        + host
        + ":"
        + port
        + ";databaseName="
        + databaseName
        + ";encrypt=true;trustServerCertificate=true";
  }

  static Connection openConnection(
      final String host,
      final int port,
      final String databaseName,
      final String username,
      final String password)
      throws SQLException {
    return DriverManager.getConnection(jdbcUrl(host, port, databaseName), username, password);
  }

  static Map<String, String> placeholders(final String appLogin, final String appPassword) {
    return Map.of("app_login", appLogin, "app_password", appPassword);
  }

  public static void migrateApplicationSchemas(
      String jdbcUrl,
      String administratorUsername,
      String administratorPassword,
      String appLogin,
      String appPassword) {
    if (!appLogin.matches("[A-Za-z][A-Za-z0-9_]{0,127}")) {
      throw new IllegalArgumentException("Unsafe test application login");
    }

    String location =
        Path.of("scripts", "database", "flyway", "wl_chat").toAbsolutePath().toString();
    var configuration =
        Flyway.configure()
            .dataSource(jdbcUrl, administratorUsername, administratorPassword)
            .locations("filesystem:" + location)
            .defaultSchema("platform")
            .schemas("platform", "identity", "messaging", "audit")
            .table("flyway_schema_history")
            .placeholders(placeholders(appLogin, appPassword));

    configuration.target(MigrationVersion.fromVersion("20260814100000")).load().migrate();
    removeHistoricalRuntimeRoles(jdbcUrl, administratorUsername, administratorPassword, appLogin);
    configuration.target(MigrationVersion.LATEST).load().migrate();
  }

  private static void removeHistoricalRuntimeRoles(
      String jdbcUrl, String administratorUsername, String administratorPassword, String appLogin) {
    String sql =
        "IF IS_ROLEMEMBER(N'db_datareader', N'"
            + appLogin
            + "') = 1 ALTER ROLE [db_datareader] DROP MEMBER ["
            + appLogin
            + "]; "
            + "IF IS_ROLEMEMBER(N'db_datawriter', N'"
            + appLogin
            + "') = 1 ALTER ROLE [db_datawriter] DROP MEMBER ["
            + appLogin
            + "];";
    try (Connection connection =
            DriverManager.getConnection(jdbcUrl, administratorUsername, administratorPassword);
        var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to remove historical runtime roles", exception);
    }
  }

  private static void quietSqlServerDriverLogs() {
    Logger.getLogger("com.microsoft.sqlserver").setLevel(Level.SEVERE);
    Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(Level.SEVERE);
    Logger.getLogger("com.microsoft.sqlserver.jdbc.internals").setLevel(Level.SEVERE);
  }
}
