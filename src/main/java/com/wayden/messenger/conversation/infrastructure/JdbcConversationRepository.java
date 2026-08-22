package com.wayden.messenger.conversation.infrastructure;

import com.wayden.messenger.conversation.application.ConversationCursorCodec.ConversationCursor;
import com.wayden.messenger.conversation.application.ConversationCursorCodec.MemberCursor;
import com.wayden.messenger.conversation.application.ConversationExceptions;
import com.wayden.messenger.conversation.application.ConversationRepository;
import com.wayden.messenger.conversation.domain.Conversation;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationMember;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.ConversationTitle;
import com.wayden.messenger.conversation.domain.ConversationType;
import com.wayden.messenger.conversation.domain.DirectParticipantPair;
import com.wayden.messenger.identity.domain.UserId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class JdbcConversationRepository implements ConversationRepository {

  private static final int UNIQUE_INDEX = 2601;
  private static final int UNIQUE_CONSTRAINT = 2627;
  private static final int DIRECT_PAIR_LOCK_TIMEOUT_MS = 10_000;
  private static final String DIRECT_PAIR_LOCK_PREFIX = "conversation-direct:";
  private static final String ACQUIRE_APPLICATION_LOCK_SQL =
      "{? = call sys.sp_getapplock(?, 'Exclusive', 'Transaction', ?)}";
  private static final String CONVERSATION_COLUMNS =
      "c.id, c.conversation_type, c.title, c.created_by, c.next_message_sequence, c.created_at, c.updated_at";
  private static final String FIND_DIRECT_SQL =
      "SELECT "
          + CONVERSATION_COLUMNS
          + " FROM [messaging].[direct_conversation_pair] p "
          + "JOIN [messaging].[conversation] c ON c.id = p.conversation_id "
          + "WHERE p.participant_low_id = ? AND p.participant_high_id = ?";
  private static final String INSERT_CONVERSATION_SQL =
      "INSERT INTO [messaging].[conversation] "
          + "(id, conversation_type, title, created_by, next_message_sequence, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";
  private static final String INSERT_MEMBER_SQL =
      "INSERT INTO [messaging].[conversation_member] "
          + "(conversation_id, user_id, conversation_role, joined_at, left_at, last_delivered_sequence, last_read_sequence) "
          + "VALUES (?, ?, ?, ?, NULL, 0, 0)";
  private static final String INSERT_PAIR_SQL =
      "INSERT INTO [messaging].[direct_conversation_pair] "
          + "(conversation_id, participant_low_id, participant_high_id) VALUES (?, ?, ?)";
  private static final String FIND_ACCESSIBLE_SQL =
      "SELECT "
          + CONVERSATION_COLUMNS
          + ", m.conversation_role FROM [messaging].[conversation] c "
          + "JOIN [messaging].[conversation_member] m ON m.conversation_id = c.id "
          + "WHERE c.id = ? AND m.user_id = ? AND m.left_at IS NULL";
  private static final String LIST_ACCESSIBLE_SQL =
      "SELECT TOP (?) "
          + CONVERSATION_COLUMNS
          + ", m.conversation_role FROM [messaging].[conversation] c "
          + "JOIN [messaging].[conversation_member] m ON m.conversation_id = c.id "
          + "WHERE m.user_id = ? AND m.left_at IS NULL "
          + "AND (? IS NULL OR c.updated_at < ? OR (c.updated_at = ? AND c.id < ?)) "
          + "ORDER BY c.updated_at DESC, c.id DESC";
  private static final String LIST_ACTIVE_MEMBERS_SQL =
      "SELECT TOP (?) m.conversation_id, m.user_id, u.username, m.conversation_role, m.joined_at, m.left_at, "
          + "m.last_delivered_sequence, m.last_read_sequence "
          + "FROM [messaging].[conversation_member] m "
          + "JOIN [identity].[user_account] u ON u.id = m.user_id "
          + "WHERE m.conversation_id = ? AND m.left_at IS NULL "
          + "AND (? IS NULL OR m.joined_at > ? OR (m.joined_at = ? AND m.user_id > ?)) "
          + "ORDER BY m.joined_at ASC, m.user_id ASC";
  private static final String FIND_MEMBERSHIP_SQL =
      "SELECT m.conversation_id, m.user_id, u.username, m.conversation_role, m.joined_at, m.left_at, "
          + "m.last_delivered_sequence, m.last_read_sequence "
          + "FROM [messaging].[conversation_member] m "
          + "JOIN [identity].[user_account] u ON u.id = m.user_id "
          + "WHERE m.conversation_id = ? AND m.user_id = ?";
  private static final String REACTIVATE_MEMBER_SQL =
      "UPDATE [messaging].[conversation_member] WITH (UPDLOCK, SERIALIZABLE) "
          + "SET conversation_role = 'MEMBER', joined_at = ?, left_at = NULL, "
          + "last_delivered_sequence = 0, last_read_sequence = 0 "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NOT NULL";
  private static final String MARK_LEFT_SQL =
      "UPDATE [messaging].[conversation_member] SET left_at = ? "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
  private static final String CHANGE_ROLE_SQL =
      "UPDATE [messaging].[conversation_member] SET conversation_role = ? "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
  private static final String TOUCH_SQL =
      "UPDATE [messaging].[conversation] SET updated_at = ? WHERE id = ?";
  private static final String FIND_ACTIVE_MEMBER_USER_IDS_SQL =
      "SELECT user_id FROM [messaging].[conversation_member] "
          + "WHERE conversation_id = ? AND left_at IS NULL "
          + "ORDER BY joined_at ASC, user_id ASC";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "DataSource is container-managed and retained for JDBC operations.")
  public JdbcConversationRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<Conversation> findDirect(DirectParticipantPair pair, boolean lockForCreate) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_DIRECT_SQL)) {
      if (lockForCreate) {
        acquireDirectPairLock(connection, pair);
      }
      statement.setObject(1, pair.low().value());
      statement.setObject(2, pair.high().value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(mapConversation(resultSet)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("find direct conversation", exception);
    }
  }

  @Override
  public void createDirect(
      Conversation conversation, DirectParticipantPair pair, Instant joinedAt) {
    try (var connection = dataSource.getConnection()) {
      insertConversation(connection, conversation);
      insertMember(connection, conversation.id(), pair.low(), ConversationRole.MEMBER, joinedAt);
      insertMember(connection, conversation.id(), pair.high(), ConversationRole.MEMBER, joinedAt);
      try (var statement = connection.prepareStatement(INSERT_PAIR_SQL)) {
        statement.setObject(1, conversation.id().value());
        statement.setObject(2, pair.low().value());
        statement.setObject(3, pair.high().value());
        statement.executeUpdate();
      }
    } catch (SQLException exception) {
      if (isUniqueViolation(exception)) {
        throw new ConversationExceptions.DuplicateDirectPairException(exception);
      }
      throw failure("create direct conversation", exception);
    }
  }

  @Override
  public void createGroup(
      Conversation conversation, UserId ownerId, List<UserId> initialMemberIds, Instant joinedAt) {
    try (var connection = dataSource.getConnection()) {
      insertConversation(connection, conversation);
      insertMember(connection, conversation.id(), ownerId, ConversationRole.OWNER, joinedAt);
      for (UserId memberId : initialMemberIds) {
        insertMember(connection, conversation.id(), memberId, ConversationRole.MEMBER, joinedAt);
      }
    } catch (SQLException exception) {
      throw failure("create group conversation", exception);
    }
  }

  @Override
  public Optional<ConversationView> findAccessible(
      ConversationId conversationId, UserId actorUserId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_ACCESSIBLE_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, actorUserId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(mapView(resultSet)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("find accessible conversation", exception);
    }
  }

  @Override
  public List<ConversationView> listAccessible(
      UserId actorUserId, ConversationCursor after, int limit) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(LIST_ACCESSIBLE_SQL)) {
      LocalDateTime afterTime = after == null ? null : toUtc(after.updatedAt());
      statement.setInt(1, limit);
      statement.setObject(2, actorUserId.value());
      statement.setObject(3, afterTime);
      statement.setObject(4, afterTime);
      statement.setObject(5, afterTime);
      statement.setObject(6, after == null ? null : after.conversationId().value());
      List<ConversationView> conversations = new ArrayList<>();
      try (var resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          conversations.add(mapView(resultSet));
        }
      }
      return conversations;
    } catch (SQLException exception) {
      throw failure("list accessible conversations", exception);
    }
  }

  @Override
  public List<ConversationMember> listActiveMembers(
      ConversationId conversationId, MemberCursor after, int limit) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(LIST_ACTIVE_MEMBERS_SQL)) {
      LocalDateTime afterTime = after == null ? null : toUtc(after.joinedAt());
      statement.setInt(1, limit);
      statement.setObject(2, conversationId.value());
      statement.setObject(3, afterTime);
      statement.setObject(4, afterTime);
      statement.setObject(5, afterTime);
      statement.setObject(6, after == null ? null : after.userId().value());
      List<ConversationMember> members = new ArrayList<>();
      try (var resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          members.add(mapMember(resultSet));
        }
      }
      return members;
    } catch (SQLException exception) {
      throw failure("list conversation members", exception);
    }
  }

  @Override
  public Optional<ConversationMember> findMembership(ConversationId conversationId, UserId userId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_MEMBERSHIP_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, userId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(mapMember(resultSet)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("find conversation membership", exception);
    }
  }

  @Override
  public void addOrReactivateMember(ConversationId conversationId, UserId userId, Instant now) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(REACTIVATE_MEMBER_SQL)) {
      statement.setObject(1, toUtc(now));
      statement.setObject(2, conversationId.value());
      statement.setObject(3, userId.value());
      if (statement.executeUpdate() == 0 && !membershipExists(connection, conversationId, userId)) {
        insertMember(connection, conversationId, userId, ConversationRole.MEMBER, now);
      }
      touch(connection, conversationId, now);
    } catch (SQLException exception) {
      if (!isUniqueViolation(exception)) {
        throw failure("add conversation member", exception);
      }
    }
  }

  @Override
  public void markMemberLeft(ConversationId conversationId, UserId userId, Instant now) {
    executeMembershipUpdate(MARK_LEFT_SQL, now, conversationId, userId, null);
  }

  @Override
  public void changeRole(
      ConversationId conversationId, UserId userId, ConversationRole role, Instant now) {
    executeMembershipUpdate(CHANGE_ROLE_SQL, now, conversationId, userId, role);
  }

  @Override
  public void transferOwnership(
      ConversationId conversationId, UserId currentOwnerId, UserId newOwnerId, Instant now) {
    try (var connection = dataSource.getConnection()) {
      try (var demote = connection.prepareStatement(CHANGE_ROLE_SQL)) {
        demote.setString(1, ConversationRole.ADMIN.name());
        demote.setObject(2, conversationId.value());
        demote.setObject(3, currentOwnerId.value());
        if (demote.executeUpdate() != 1) {
          throw new ConversationExceptions.OwnershipRequiredException(
              "Current owner membership changed before transfer");
        }
      }
      try (var promote = connection.prepareStatement(CHANGE_ROLE_SQL)) {
        promote.setString(1, ConversationRole.OWNER.name());
        promote.setObject(2, conversationId.value());
        promote.setObject(3, newOwnerId.value());
        if (promote.executeUpdate() != 1) {
          throw new ConversationExceptions.OwnershipRequiredException(
              "New owner must be an active group member");
        }
      }
      touch(connection, conversationId, now);
    } catch (SQLException exception) {
      throw failure("transfer conversation ownership", exception);
    }
  }

  @Override
  public List<UserId> findActiveMemberUserIds(ConversationId conversationId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_ACTIVE_MEMBER_USER_IDS_SQL)) {
      statement.setObject(1, conversationId.value());
      try (var resultSet = statement.executeQuery()) {
        List<UserId> userIds = new ArrayList<>();
        while (resultSet.next()) {
          userIds.add(new UserId(resultSet.getObject("user_id", UUID.class)));
        }
        return Collections.unmodifiableList(userIds);
      }
    } catch (SQLException exception) {
      throw failure("find active member user ids for conversation", exception);
    }
  }

  private void executeMembershipUpdate(
      String sql,
      Instant now,
      ConversationId conversationId,
      UserId userId,
      ConversationRole role) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(sql)) {
      if (role == null) {
        statement.setObject(1, toUtc(now));
      } else {
        statement.setString(1, role.name());
      }
      statement.setObject(2, conversationId.value());
      statement.setObject(3, userId.value());
      statement.executeUpdate();
      touch(connection, conversationId, now);
    } catch (SQLException exception) {
      throw failure("update conversation membership", exception);
    }
  }

  private static void insertConversation(Connection connection, Conversation conversation)
      throws SQLException {
    try (var statement = connection.prepareStatement(INSERT_CONVERSATION_SQL)) {
      statement.setObject(1, conversation.id().value());
      statement.setString(2, conversation.type().name());
      statement.setString(3, conversation.title() == null ? null : conversation.title().value());
      statement.setObject(4, conversation.createdBy().value());
      statement.setLong(5, conversation.nextMessageSequence());
      statement.setObject(6, toUtc(conversation.createdAt()));
      statement.setObject(7, toUtc(conversation.updatedAt()));
      statement.executeUpdate();
    }
  }

  private static void insertMember(
      Connection connection,
      ConversationId conversationId,
      UserId userId,
      ConversationRole role,
      Instant joinedAt)
      throws SQLException {
    try (var statement = connection.prepareStatement(INSERT_MEMBER_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, userId.value());
      statement.setString(3, role.name());
      statement.setObject(4, toUtc(joinedAt));
      statement.executeUpdate();
    }
  }

  private static boolean membershipExists(
      Connection connection, ConversationId conversationId, UserId userId) throws SQLException {
    try (var statement = connection.prepareStatement(FIND_MEMBERSHIP_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, userId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static void acquireDirectPairLock(Connection connection, DirectParticipantPair pair)
      throws SQLException {
    try (var statement = connection.prepareCall(ACQUIRE_APPLICATION_LOCK_SQL)) {
      statement.registerOutParameter(1, Types.INTEGER);
      statement.setString(2, directPairLockResource(pair));
      statement.setInt(3, DIRECT_PAIR_LOCK_TIMEOUT_MS);
      statement.execute();
      int result = statement.getInt(1);
      if (result < 0) {
        throw new SQLException("Could not acquire direct conversation pair lock; result=" + result);
      }
    }
  }

  private static String directPairLockResource(DirectParticipantPair pair) {
    return DIRECT_PAIR_LOCK_PREFIX + pair.low().value() + ":" + pair.high().value();
  }

  private static void touch(Connection connection, ConversationId conversationId, Instant updatedAt)
      throws SQLException {
    try (var statement = connection.prepareStatement(TOUCH_SQL)) {
      statement.setObject(1, toUtc(updatedAt));
      statement.setObject(2, conversationId.value());
      statement.executeUpdate();
    }
  }

  private static ConversationView mapView(ResultSet resultSet) throws SQLException {
    return new ConversationView(
        mapConversation(resultSet),
        ConversationRole.valueOf(resultSet.getString("conversation_role")));
  }

  private static Conversation mapConversation(ResultSet resultSet) throws SQLException {
    ConversationType type = ConversationType.valueOf(resultSet.getString("conversation_type"));
    String title = resultSet.getString("title");
    return new Conversation(
        new ConversationId(resultSet.getObject("id", UUID.class)),
        type,
        title == null ? null : new ConversationTitle(title),
        new UserId(resultSet.getObject("created_by", UUID.class)),
        resultSet.getLong("next_message_sequence"),
        fromUtc(resultSet.getObject("created_at", LocalDateTime.class)),
        fromUtc(resultSet.getObject("updated_at", LocalDateTime.class)));
  }

  private static ConversationMember mapMember(ResultSet resultSet) throws SQLException {
    LocalDateTime leftAt = resultSet.getObject("left_at", LocalDateTime.class);
    return new ConversationMember(
        new ConversationId(resultSet.getObject("conversation_id", UUID.class)),
        new UserId(resultSet.getObject("user_id", UUID.class)),
        resultSet.getString("username"),
        ConversationRole.valueOf(resultSet.getString("conversation_role")),
        fromUtc(resultSet.getObject("joined_at", LocalDateTime.class)),
        leftAt == null ? null : fromUtc(leftAt),
        resultSet.getLong("last_delivered_sequence"),
        resultSet.getLong("last_read_sequence"));
  }

  private static LocalDateTime toUtc(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant fromUtc(LocalDateTime value) {
    return value.toInstant(ZoneOffset.UTC);
  }

  private static boolean isUniqueViolation(SQLException exception) {
    return exception.getErrorCode() == UNIQUE_INDEX
        || exception.getErrorCode() == UNIQUE_CONSTRAINT;
  }

  private static ConversationExceptions.InternalException failure(
      String operation, SQLException exception) {
    return new ConversationExceptions.InternalException(operation, exception);
  }
}
