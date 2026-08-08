package com.wayden.messenger.bootstrap;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.MSSQLServerContainer;

public class IdentitySqlServerTestResource implements QuarkusTestResourceLifecycleManager {

  private static final String DB_PASSWORD = "StrongPassw0rd!";
  private static final String APP_LOGIN = "wl_chat_app";
  private static final String APP_PASSWORD = "AppPassw0rd!";

  private static MSSQLServerContainer<?> sqlServer;

  @Override
  public Map<String, String> start() {
    if (sqlServer == null) {
      sqlServer =
          new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
              .acceptLicense()
              .withPassword(DB_PASSWORD);
      sqlServer.start();
      migrateMaster();
      migrateApplicationSchemas();
    }

    return Map.of(
        "quarkus.datasource.db-kind",
        "mssql",
        "quarkus.datasource.jdbc.url",
        jdbcUrl("wl_chat"),
        "quarkus.datasource.username",
        APP_LOGIN,
        "quarkus.datasource.password",
        APP_PASSWORD,
        "quarkus.datasource.health.enabled",
        "false",
        "quarkus.flyway.migrate-at-start",
        "false");
  }

  @Override
  public void stop() {
    if (sqlServer != null) {
      sqlServer.stop();
      sqlServer = null;
    }
  }

  public static String jdbcUrl(String databaseName) {
    return TestSqlSupport.jdbcUrl(sqlServer.getHost(), sqlServer.getMappedPort(1433), databaseName);
  }

  public static String saPassword() {
    return DB_PASSWORD;
  }

  private static void migrateMaster() {
    String location =
        Path.of("scripts", "database", "flyway", "master").toAbsolutePath().toString();
    Flyway.configure()
        .dataSource(jdbcUrl("master"), "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .load()
        .migrate();
  }

  private static void migrateApplicationSchemas() {
    String location =
        Path.of("scripts", "database", "flyway", "wl_chat").toAbsolutePath().toString();
    Flyway.configure()
        .dataSource(jdbcUrl("wl_chat"), "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .defaultSchema("platform")
        .schemas("platform", "identity", "messaging", "audit")
        .table("flyway_schema_history")
        .placeholders(TestSqlSupport.placeholders(APP_LOGIN, APP_PASSWORD))
        .load()
        .migrate();
  }
}
