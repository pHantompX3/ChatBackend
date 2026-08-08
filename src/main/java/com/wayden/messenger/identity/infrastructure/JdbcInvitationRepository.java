package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.InvitationRepository;
import com.wayden.messenger.identity.domain.Invitation;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import com.wayden.messenger.identity.domain.UserId;
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
public class JdbcInvitationRepository implements InvitationRepository {

  private static final String INSERT_SQL =
      "INSERT INTO [identity].[invitation] "
          + "(id, token_hash, created_by, expires_at, redeemed_at, redeemed_by, revoked_at, created_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String FIND_BY_HASH_SQL =
      "SELECT id, token_hash, created_by, expires_at, redeemed_at, redeemed_by, revoked_at, created_at "
          + "FROM [identity].[invitation] WHERE token_hash = ?";

  private static final String MARK_REVOKED_SQL =
      "UPDATE [identity].[invitation] "
          + "SET revoked_at = ? "
          + "WHERE id = ? AND revoked_at IS NULL AND redeemed_at IS NULL";

  private static final String MARK_REDEEMED_SQL =
      "UPDATE [identity].[invitation] "
          + "SET redeemed_at = ?, redeemed_by = ? "
          + "WHERE id = ? AND redeemed_at IS NULL AND revoked_at IS NULL";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "DataSource is container-managed infrastructure and intentionally retained for repository operations.")
  public JdbcInvitationRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Invitation save(Invitation invitation) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setObject(1, invitation.id().value());
      statement.setBytes(2, invitation.tokenHash().value());
      statement.setObject(3, invitation.createdBy().value());
      statement.setObject(4, toUtcLocalDateTime(invitation.expiresAt()));
      statement.setObject(5, toNullableUtcLocalDateTime(invitation.redeemedAt()));
      statement.setObject(
          6, invitation.redeemedBy() == null ? null : invitation.redeemedBy().value());
      statement.setObject(7, toNullableUtcLocalDateTime(invitation.revokedAt()));
      statement.setObject(8, toUtcLocalDateTime(invitation.createdAt()));
      statement.executeUpdate();
      return invitation;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to save invitation", e);
    }
  }

  @Override
  public Optional<Invitation> findByTokenHash(InvitationTokenHash tokenHash) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_BY_HASH_SQL)) {
      statement.setBytes(1, tokenHash.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(mapInvitation(resultSet));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to find invitation by token hash", e);
    }
  }

  @Override
  public boolean markRevoked(InvitationId invitationId, UserId actorUserId, Instant revokedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(MARK_REVOKED_SQL)) {
      statement.setObject(1, toUtcLocalDateTime(revokedAt));
      statement.setObject(2, invitationId.value());
      return statement.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to revoke invitation", e);
    }
  }

  @Override
  public boolean markRedeemed(InvitationId invitationId, UserId actorUserId, Instant redeemedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(MARK_REDEEMED_SQL)) {
      statement.setObject(1, toUtcLocalDateTime(redeemedAt));
      statement.setObject(2, actorUserId.value());
      statement.setObject(3, invitationId.value());
      return statement.executeUpdate() == 1;
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to redeem invitation", e);
    }
  }

  private Invitation mapInvitation(ResultSet resultSet) throws SQLException {
    UUID id = resultSet.getObject("id", UUID.class);
    byte[] tokenHash = resultSet.getBytes("token_hash");
    UUID createdBy = resultSet.getObject("created_by", UUID.class);
    Instant expiresAt =
        fromUtcLocalDateTime(resultSet.getObject("expires_at", LocalDateTime.class));
    Instant redeemedAt =
        fromNullableUtcLocalDateTime(resultSet.getObject("redeemed_at", LocalDateTime.class));
    UUID redeemedBy = resultSet.getObject("redeemed_by", UUID.class);
    Instant revokedAt =
        fromNullableUtcLocalDateTime(resultSet.getObject("revoked_at", LocalDateTime.class));
    Instant createdAt =
        fromUtcLocalDateTime(resultSet.getObject("created_at", LocalDateTime.class));

    return new Invitation(
        new InvitationId(id),
        new InvitationTokenHash(tokenHash),
        new UserId(createdBy),
        expiresAt,
        redeemedAt,
        redeemedBy == null ? null : new UserId(redeemedBy),
        revokedAt,
        createdAt);
  }

  private static LocalDateTime toUtcLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static LocalDateTime toNullableUtcLocalDateTime(Instant instant) {
    return instant == null ? null : toUtcLocalDateTime(instant);
  }

  private static Instant fromUtcLocalDateTime(LocalDateTime localDateTime) {
    return localDateTime.toInstant(ZoneOffset.UTC);
  }

  private static Instant fromNullableUtcLocalDateTime(LocalDateTime localDateTime) {
    return localDateTime == null ? null : fromUtcLocalDateTime(localDateTime);
  }
}
