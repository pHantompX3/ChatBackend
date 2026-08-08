package com.wayden.messenger.bootstrap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TestSqlSupport {

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

  private static void quietSqlServerDriverLogs() {
    Logger.getLogger("com.microsoft.sqlserver").setLevel(Level.SEVERE);
    Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(Level.SEVERE);
    Logger.getLogger("com.microsoft.sqlserver.jdbc.internals").setLevel(Level.SEVERE);
  }
}
