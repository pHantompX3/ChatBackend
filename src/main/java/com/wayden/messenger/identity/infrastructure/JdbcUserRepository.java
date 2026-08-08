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
  private static final String INSERT_SQL =
      "INSERT INTO [identity].[user_account] "
          + "(id, username, normalized_username, password_hash, system_role, status, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
}
