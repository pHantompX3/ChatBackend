package com.wayden.messenger.session.infrastructure;

import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.application.SessionRepository;
import com.wayden.messenger.session.domain.Session;
import com.wayden.messenger.session.domain.SessionId;
import com.wayden.messenger.session.domain.SessionStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class JdbcSessionRepository implements SessionRepository {

  private static final String INSERT_SQL =
      "INSERT INTO [identity].[session] "
          + "(id, user_id, token_hash, created_at, expires_at, last_seen_at, revoked_at, user_agent, source_address, status) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String FIND_BY_TOKEN_HASH_SQL =
      "SELECT id, user_id, token_hash, created_at, expires_at, last_seen_at, revoked_at, user_agent, source_address, status "
          + "FROM [identity].[session] WHERE token_hash = ?";
  private static final String REVOKE_SQL =
      "UPDATE [identity].[session] SET revoked_at = ?, status = ? WHERE id = ?";
  private static final String REVOKE_ALL_FOR_USER_SQL =
      "UPDATE [identity].[session] SET revoked_at = ?, status = ? "
          + "WHERE user_id = ? AND status = ? AND revoked_at IS NULL";
  private static final String TOUCH_SQL =
      "UPDATE [identity].[session] SET last_seen_at = ? WHERE id = ?";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "DataSource is container-managed infrastructure and intentionally retained for repository operations.")
  public JdbcSessionRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Session save(Session session) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setObject(1, session.id().value());
      statement.setObject(2, session.userId().value());
      statement.setBytes(3, session.tokenHash());
      statement.setObject(4, toUtcLocalDateTime(session.createdAt()));
      statement.setObject(5, toUtcLocalDateTime(session.expiresAt()));
      setNullableLocalDateTime(statement, 6, session.lastSeenAt());
      setNullableLocalDateTime(statement, 7, session.revokedAt());
      statement.setString(8, session.userAgent());
      statement.setString(9, session.sourceAddress());
      statement.setString(10, session.status().name());
      statement.executeUpdate();
      return session;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to save session", e);
    }
  }

  @Override
  public Optional<Session> findByTokenHash(byte[] tokenHash) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_BY_TOKEN_HASH_SQL)) {
      statement.setBytes(1, tokenHash);
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(mapSession(resultSet));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to find session by token hash", e);
    }
  }

  @Override
  public boolean revoke(SessionId sessionId, Instant revokedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(REVOKE_SQL)) {
      statement.setObject(1, toUtcLocalDateTime(revokedAt));
      statement.setString(2, SessionStatus.REVOKED.name());
      statement.setObject(3, sessionId.value());
      return statement.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to revoke session", e);
    }
  }

  @Override
  public int revokeAllForUser(UserId userId, Instant revokedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(REVOKE_ALL_FOR_USER_SQL)) {
      statement.setObject(1, toUtcLocalDateTime(revokedAt));
      statement.setString(2, SessionStatus.REVOKED.name());
      statement.setObject(3, userId.value());
      statement.setString(4, SessionStatus.ACTIVE.name());
      return statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to revoke all sessions for user", e);
    }
  }

  @Override
  public void touch(SessionId sessionId, Instant lastSeenAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(TOUCH_SQL)) {
      statement.setObject(1, toUtcLocalDateTime(lastSeenAt));
      statement.setObject(2, sessionId.value());
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to touch session", e);
    }
  }

  private Session mapSession(ResultSet resultSet) throws SQLException {
    UUID id = resultSet.getObject("id", UUID.class);
    UUID userId = resultSet.getObject("user_id", UUID.class);
    byte[] tokenHash = resultSet.getBytes("token_hash");
    Instant createdAt =
        fromUtcLocalDateTime(resultSet.getObject("created_at", LocalDateTime.class));
    Instant expiresAt =
        fromUtcLocalDateTime(resultSet.getObject("expires_at", LocalDateTime.class));
    Instant lastSeenAt =
        resultSet.getObject("last_seen_at", LocalDateTime.class) == null
            ? null
            : fromUtcLocalDateTime(resultSet.getObject("last_seen_at", LocalDateTime.class));
    Instant revokedAt =
        resultSet.getObject("revoked_at", LocalDateTime.class) == null
            ? null
            : fromUtcLocalDateTime(resultSet.getObject("revoked_at", LocalDateTime.class));
    String userAgent = resultSet.getString("user_agent");
    String sourceAddress = resultSet.getString("source_address");
    SessionStatus status = SessionStatus.valueOf(resultSet.getString("status"));

    return new Session(
        new SessionId(id),
        new UserId(userId),
        tokenHash,
        createdAt,
        expiresAt,
        lastSeenAt,
        revokedAt,
        userAgent,
        sourceAddress,
        status);
  }

  private static LocalDateTime toUtcLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant fromUtcLocalDateTime(LocalDateTime localDateTime) {
    return localDateTime.toInstant(ZoneOffset.UTC);
  }

  private static void setNullableLocalDateTime(
      PreparedStatement statement, int index, Instant value) throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.TIMESTAMP);
    } else {
      statement.setObject(index, toUtcLocalDateTime(value));
    }
  }
}
