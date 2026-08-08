package com.wayden.messenger.bootstrap;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.MSSQLServerContainer;

public class IdentitySqlServerTestResource implements QuarkusTestResourceLifecycleManager {

  private static final String DB_PASSWORD = "StrongPassw0rd!";
  private static final String APP_LOGIN = "wl_chat_app";
  private static final String APP_PASSWORD = "AppPassw0rd!";

  private static MSSQLServerContainer<?> sqlServer;
  private static final Object CONTAINER_LOCK = new Object();
  private boolean ownsContainer;

  @Override
  @SuppressWarnings("resource")
  public Map<String, String> start() {
    synchronized (CONTAINER_LOCK) {
      if (sqlServer == null) {
        sqlServer =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withPassword(DB_PASSWORD);
        ownsContainer = true;
        sqlServer.start();
        retry("migrate master bootstrap", this::migrateMaster, 5, Duration.ofSeconds(2));
        retry(
            "migrate application schemas",
            this::migrateApplicationSchemas,
            5,
            Duration.ofSeconds(2));
      } else {
        ownsContainer = false;
      }
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
    synchronized (CONTAINER_LOCK) {
      if (ownsContainer && sqlServer != null) {
        sqlServer.stop();
        sqlServer = null;
      }
      ownsContainer = false;
    }
  }

  public static String jdbcUrl(String databaseName) {
    return TestSqlSupport.jdbcUrl(sqlServer.getHost(), sqlServer.getMappedPort(1433), databaseName);
  }

  public static String saPassword() {
    return DB_PASSWORD;
  }

  static void retry(String operation, ThrowingRunnable action, int attempts, Duration delay) {
    retry(
        operation,
        () -> {
          action.run();
          return null;
        },
        attempts,
        delay);
  }

  static <T> T retry(String operation, ThrowingSupplier<T> action, int attempts, Duration delay) {
    Exception lastFailure = null;
    for (int attempt = 1; attempt <= attempts; attempt++) {
      try {
        return action.get();
      } catch (Exception exception) {
        lastFailure = exception;
        if (attempt == attempts) {
          break;
        }
        if (!delay.isZero() && !delay.isNegative()) {
          try {
            Thread.sleep(delay.toMillis());
          } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while retrying " + operation, interruptedException);
          }
        }
      }
    }

    if (lastFailure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new IllegalStateException(
        "Failed to " + operation + " after " + attempts + " attempts", lastFailure);
  }

  private void migrateMaster() {
    String location =
        Path.of("scripts", "database", "flyway", "master").toAbsolutePath().toString();
    Flyway.configure()
        .dataSource(jdbcUrl("master"), "sa", DB_PASSWORD)
        .locations("filesystem:" + location)
        .load()
        .migrate();
  }

  private void migrateApplicationSchemas() {
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

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
