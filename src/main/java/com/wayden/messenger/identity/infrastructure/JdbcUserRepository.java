package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.IdentityExceptions;
import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.PasswordHash;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class JdbcUserRepository implements UserRepository {

  private static final String EXISTS_ANY_SQL = "SELECT TOP 1 id FROM [identity].[user_account]";
  private static final String FIND_BY_ID_SQL =
      "SELECT id, username, normalized_username, password_hash, system_role, status, created_at, updated_at "
          + "FROM [identity].[user_account] WHERE id = ?";
  private static final String FIND_BY_NORMALIZED_SQL =
      "SELECT id, username, normalized_username, password_hash, system_role, status, created_at, updated_at "
          + "FROM [identity].[user_account] WHERE normalized_username = ?";
  private static final String SEARCH_ACTIVE_BY_PREFIX_SQL =
      "SELECT TOP (?) id, username, normalized_username, password_hash, system_role, status, created_at, updated_at "
          + "FROM [identity].[user_account] "
          + "WHERE status = 'ACTIVE' AND id <> ? AND normalized_username LIKE ? ESCAPE '\\' "
          + "AND (? IS NULL OR normalized_username > ? OR (normalized_username = ? AND id > ?)) "
          + "ORDER BY normalized_username ASC, id ASC";
  private static final String INSERT_SQL =
      "INSERT INTO [identity].[user_account] "
          + "(id, username, normalized_username, password_hash, system_role, status, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String INSERT_FIRST_ADMIN_SQL =
      "INSERT INTO [identity].[user_account] "
          + "(id, username, normalized_username, password_hash, system_role, status, created_at, updated_at) "
          + "SELECT ?, ?, ?, ?, ?, ?, ?, ? "
          + "WHERE NOT EXISTS (SELECT 1 FROM [identity].[user_account] WITH (UPDLOCK, HOLDLOCK))";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "DataSource is container-managed infrastructure and intentionally retained for repository operations.")
  public JdbcUserRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public boolean existsAnyUser() {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(EXISTS_ANY_SQL);
        var resultSet = statement.executeQuery()) {
      return resultSet.next();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to check if any users exist", e);
    }
  }

  @Override
  public Optional<User> findById(UserId userId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
      statement.setObject(1, userId.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(mapUser(resultSet));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to find user by id", e);
    }
  }

  @Override
  public Optional<User> findByNormalizedUsername(NormalizedUsername normalizedUsername) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_BY_NORMALIZED_SQL)) {
      statement.setString(1, normalizedUsername.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(mapUser(resultSet));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to find user by normalized username", e);
    }
  }

  @Override
  public List<User> searchActiveByUsernamePrefix(
      NormalizedUsername prefix,
      NormalizedUsername afterUsername,
      UserId afterUserId,
      UserId excludedUserId,
      int limit) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(SEARCH_ACTIVE_BY_PREFIX_SQL)) {
      String after = afterUsername == null ? null : afterUsername.value();
      statement.setInt(1, limit);
      statement.setObject(2, excludedUserId.value());
      statement.setString(3, escapeLike(prefix.value()) + "%");
      statement.setString(4, after);
      statement.setString(5, after);
      statement.setString(6, after);
      statement.setObject(7, afterUserId == null ? null : afterUserId.value());
      List<User> users = new ArrayList<>();
      try (var resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          users.add(mapUser(resultSet));
        }
      }
      return users;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to search active users", e);
    }
  }

  @Override
  public User save(User user) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setObject(1, user.id().value());
      statement.setString(2, user.username());
      statement.setString(3, user.normalizedUsername().value());
      statement.setString(4, user.passwordHash().value());
      statement.setString(5, user.systemRole().name());
      statement.setString(6, user.status().name());
      statement.setObject(7, toUtcLocalDateTime(user.createdAt()));
      statement.setObject(8, toUtcLocalDateTime(user.updatedAt()));
      statement.executeUpdate();
      return user;
    } catch (SQLException e) {
      if (e.getErrorCode() == SqlServerErrorCodes.UNIQUE_INDEX
          || e.getErrorCode() == SqlServerErrorCodes.UNIQUE_CONSTRAINT) {
        throw new IdentityExceptions.DuplicateUsernameException(
            "Username is already in use: " + user.normalizedUsername().value());
      }
      throw new IllegalStateException("Failed to save user", e);
    }
  }

  @Override
  public User saveFirstAdminIfAbsent(User user) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(INSERT_FIRST_ADMIN_SQL)) {
      statement.setObject(1, user.id().value());
      statement.setString(2, user.username());
      statement.setString(3, user.normalizedUsername().value());
      statement.setString(4, user.passwordHash().value());
      statement.setString(5, user.systemRole().name());
      statement.setString(6, user.status().name());
      statement.setObject(7, toUtcLocalDateTime(user.createdAt()));
      statement.setObject(8, toUtcLocalDateTime(user.updatedAt()));
      int insertedRowCount = statement.executeUpdate();
      if (insertedRowCount == 0) {
        throw new IdentityExceptions.BootstrapAlreadyCompletedException();
      }
      return user;
    } catch (SQLException e) {
      if (e.getErrorCode() == SqlServerErrorCodes.UNIQUE_INDEX
          || e.getErrorCode() == SqlServerErrorCodes.UNIQUE_CONSTRAINT) {
        throw new IdentityExceptions.DuplicateUsernameException(
            "Username is already in use: " + user.normalizedUsername().value());
      }
      throw new IllegalStateException("Failed to bootstrap first admin", e);
    }
  }

  private User mapUser(ResultSet resultSet) throws SQLException {
    UUID id = resultSet.getObject("id", UUID.class);
    String username = resultSet.getString("username");
    String normalizedUsername = resultSet.getString("normalized_username");
    String passwordHash = resultSet.getString("password_hash");
    String systemRole = resultSet.getString("system_role");
    String status = resultSet.getString("status");
    Instant createdAt =
        fromUtcLocalDateTime(resultSet.getObject("created_at", LocalDateTime.class));
    Instant updatedAt =
        fromUtcLocalDateTime(resultSet.getObject("updated_at", LocalDateTime.class));

    return new User(
        new UserId(id),
        username,
        new NormalizedUsername(normalizedUsername),
        new PasswordHash(passwordHash),
        SystemRole.valueOf(systemRole),
        UserStatus.valueOf(status),
        createdAt,
        updatedAt);
  }

  private static LocalDateTime toUtcLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant fromUtcLocalDateTime(LocalDateTime localDateTime) {
    return localDateTime.toInstant(ZoneOffset.UTC);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
