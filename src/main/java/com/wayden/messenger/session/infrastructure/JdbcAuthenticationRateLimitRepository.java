package com.wayden.messenger.session.infrastructure;

import com.wayden.messenger.session.application.AuthenticationRateLimitRepository;
import com.wayden.messenger.session.application.SessionExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRES_NEW)
public class JdbcAuthenticationRateLimitRepository implements AuthenticationRateLimitRepository {

  private static final String DATABASE_TIME_SQL = "SELECT SYSUTCDATETIME()";
  private static final String FIND_SQL =
      "SELECT attempt_count, window_expires_at "
          + "FROM [identity].[authentication_rate_limit] WITH (UPDLOCK, HOLDLOCK) "
          + "WHERE scope_type = ? AND scope_hash = ?";
  private static final String INSERT_SQL =
      "INSERT INTO [identity].[authentication_rate_limit] "
          + "(scope_type, scope_hash, window_started_at, window_expires_at, attempt_count, updated_at) "
          + "VALUES (?, ?, ?, ?, 1, ?)";
  private static final String RESET_SQL =
      "UPDATE [identity].[authentication_rate_limit] SET window_started_at = ?, "
          + "window_expires_at = ?, attempt_count = 1, updated_at = ? "
          + "WHERE scope_type = ? AND scope_hash = ?";
  private static final String INCREMENT_SQL =
      "UPDATE [identity].[authentication_rate_limit] SET attempt_count = attempt_count + 1, "
          + "updated_at = ? WHERE scope_type = ? AND scope_hash = ?";
  private static final String CLEANUP_SQL =
      "DELETE TOP (100) FROM [identity].[authentication_rate_limit] "
          + "WHERE window_expires_at < DATEADD(day, -1, ?)";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "DataSource is a container-managed JDBC connection factory.")
  public JdbcAuthenticationRateLimitRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Decision reserve(
      byte[] accountHash,
      byte[] sourceHash,
      int accountLimit,
      Duration accountWindow,
      int sourceLimit,
      Duration sourceWindow) {
    try (var connection = dataSource.getConnection()) {
      LocalDateTime now = databaseTime(connection);
      ScopeDecision account =
          reserveScope(connection, "ACCOUNT", accountHash, accountLimit, accountWindow, now);
      ScopeDecision source =
          reserveScope(connection, "SOURCE", sourceHash, sourceLimit, sourceWindow, now);
      cleanup(connection, now);
      if (account.allowed() && source.allowed()) {
        return Decision.permitted();
      }
      long retry = Math.max(account.retryAfterSeconds(), source.retryAfterSeconds());
      String scope =
          !account.allowed() && !source.allowed()
              ? "ACCOUNT,SOURCE"
              : (!account.allowed() ? "ACCOUNT" : "SOURCE");
      return Decision.rejected(scope, retry);
    } catch (SQLException exception) {
      throw new SessionExceptions.InternalException(
          "Authentication throttle unavailable", exception);
    }
  }

  private static ScopeDecision reserveScope(
      Connection connection,
      String scope,
      byte[] hash,
      int limit,
      Duration window,
      LocalDateTime now)
      throws SQLException {
    try (var find = connection.prepareStatement(FIND_SQL)) {
      find.setString(1, scope);
      find.setBytes(2, hash);
      try (var result = find.executeQuery()) {
        if (!result.next()) {
          insert(connection, scope, hash, now, window);
          return ScopeDecision.allowedDecision();
        }
        int attempts = result.getInt("attempt_count");
        LocalDateTime expiresAt = result.getObject("window_expires_at", LocalDateTime.class);
        if (!expiresAt.isAfter(now)) {
          reset(connection, scope, hash, now, window);
          return ScopeDecision.allowedDecision();
        }
        if (attempts >= limit) {
          Duration remaining =
              Duration.between(now.toInstant(ZoneOffset.UTC), expiresAt.toInstant(ZoneOffset.UTC));
          long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
          return ScopeDecision.rejected(seconds);
        }
      }
    }
    try (var increment = connection.prepareStatement(INCREMENT_SQL)) {
      increment.setObject(1, now);
      increment.setString(2, scope);
      increment.setBytes(3, hash);
      increment.executeUpdate();
    }
    return ScopeDecision.allowedDecision();
  }

  private static LocalDateTime databaseTime(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement(DATABASE_TIME_SQL);
        var result = statement.executeQuery()) {
      result.next();
      return result.getObject(1, LocalDateTime.class);
    }
  }

  private static void insert(
      Connection connection, String scope, byte[] hash, LocalDateTime now, Duration window)
      throws SQLException {
    try (var statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setString(1, scope);
      statement.setBytes(2, hash);
      statement.setObject(3, now);
      statement.setObject(4, now.plus(window));
      statement.setObject(5, now);
      statement.executeUpdate();
    }
  }

  private static void reset(
      Connection connection, String scope, byte[] hash, LocalDateTime now, Duration window)
      throws SQLException {
    try (var statement = connection.prepareStatement(RESET_SQL)) {
      statement.setObject(1, now);
      statement.setObject(2, now.plus(window));
      statement.setObject(3, now);
      statement.setString(4, scope);
      statement.setBytes(5, hash);
      statement.executeUpdate();
    }
  }

  private static void cleanup(Connection connection, LocalDateTime now) throws SQLException {
    try (var statement = connection.prepareStatement(CLEANUP_SQL)) {
      statement.setObject(1, now);
      statement.executeUpdate();
    }
  }

  private record ScopeDecision(boolean allowed, long retryAfterSeconds) {
    private static ScopeDecision allowedDecision() {
      return new ScopeDecision(true, 0);
    }

    private static ScopeDecision rejected(long retryAfterSeconds) {
      return new ScopeDecision(false, retryAfterSeconds);
    }
  }
}
