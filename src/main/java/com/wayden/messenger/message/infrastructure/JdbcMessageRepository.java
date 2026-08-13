package com.wayden.messenger.message.infrastructure;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.ConversationType;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageExceptions;
import com.wayden.messenger.message.application.MessageRepository;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageBody;
import com.wayden.messenger.message.domain.MessageId;
import com.wayden.messenger.message.domain.MessageType;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class JdbcMessageRepository implements MessageRepository {

  private static final int UNIQUE_INDEX = 2601;
  private static final int UNIQUE_CONSTRAINT = 2627;
  private static final int DEADLOCK_VICTIM = 1205;
  private static final String CLIENT_KEY_CONSTRAINT = "uq_messaging_message_client_id";
  private static final String SEQUENCE_CONSTRAINT = "uq_messaging_message_sequence";
  private static final String MESSAGE_COLUMNS =
      "m.id, m.conversation_id, m.sender_id, m.client_message_id, m.sequence_number, "
          + "m.message_type, m.body, m.created_at, m.edited_at, m.deleted_at";
  private static final String FIND_ACCESS_SQL =
      "SELECT c.conversation_type, actor.conversation_role "
          + "FROM [messaging].[conversation_member] actor "
          + "JOIN [messaging].[conversation] c ON c.id = actor.conversation_id "
          + "WHERE actor.conversation_id = ? AND actor.user_id = ? AND actor.left_at IS NULL";
  private static final String FIND_ACCESS_FOR_UPDATE_SQL =
      "SELECT c.conversation_type, actor.conversation_role "
          + "FROM [messaging].[conversation_member] actor WITH (UPDLOCK) "
          + "JOIN [messaging].[conversation] c ON c.id = actor.conversation_id "
          + "WHERE actor.conversation_id = ? AND actor.user_id = ? AND actor.left_at IS NULL";
  private static final String FIND_BY_CLIENT_ID_SQL =
      "SELECT "
          + MESSAGE_COLUMNS
          + " FROM [messaging].[message] m WHERE m.sender_id = ? AND m.client_message_id = ?";
  private static final String FIND_BY_ID_SQL =
      "SELECT "
          + MESSAGE_COLUMNS
          + " FROM [messaging].[message] m WHERE m.conversation_id = ? AND m.id = ?";
  private static final String FIND_BY_ID_FOR_UPDATE_SQL =
      "SELECT "
          + MESSAGE_COLUMNS
          + " FROM [messaging].[message] m WITH (UPDLOCK) "
          + "WHERE m.conversation_id = ? AND m.id = ?";
  private static final String ALLOCATE_SEQUENCE_SQL =
      "UPDATE [messaging].[conversation] SET next_message_sequence = next_message_sequence + 1, "
          + "updated_at = ? OUTPUT INSERTED.next_message_sequence - 1 WHERE id = ?";
  private static final String INSERT_SQL =
      "INSERT INTO [messaging].[message] "
          + "(id, conversation_id, sender_id, client_message_id, sequence_number, message_type, "
          + "body, created_at, edited_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String LIST_AFTER_SQL =
      "SELECT TOP (?) "
          + MESSAGE_COLUMNS
          + " FROM [messaging].[message] m "
          + "JOIN [messaging].[conversation_member] actor "
          + "ON actor.conversation_id = m.conversation_id AND actor.user_id = ? "
          + "AND actor.left_at IS NULL "
          + "WHERE m.conversation_id = ? AND m.sequence_number > ? "
          + "ORDER BY m.sequence_number ASC";
  private static final String EDIT_SQL =
      "UPDATE [messaging].[message] SET body = ?, edited_at = ? "
          + "WHERE id = ? AND message_type = 'TEXT' AND deleted_at IS NULL";
  private static final String DELETE_SQL =
      "UPDATE [messaging].[message] SET body = NULL, deleted_at = ? "
          + "WHERE id = ? AND deleted_at IS NULL";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "DataSource is container-managed and retained for JDBC operations.")
  public JdbcMessageRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<ActorAccess> findActiveAccess(
      ConversationId conversationId, UserId actorId, boolean lockForMutation) {
    String sql = lockForMutation ? FIND_ACCESS_FOR_UPDATE_SQL : FIND_ACCESS_SQL;
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(sql)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, actorId.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new ActorAccess(
                ConversationType.valueOf(resultSet.getString("conversation_type")),
                ConversationRole.valueOf(resultSet.getString("conversation_role"))));
      }
    } catch (SQLException exception) {
      throw failure("authorize message access", exception);
    }
  }

  @Override
  public Optional<Message> findByClientMessageId(UserId senderId, ClientMessageId clientMessageId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_BY_CLIENT_ID_SQL)) {
      statement.setObject(1, senderId.value());
      statement.setObject(2, clientMessageId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(mapMessage(resultSet)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("resolve message idempotency", exception);
    }
  }

  @Override
  public Optional<Message> findById(
      ConversationId conversationId, MessageId messageId, boolean lockForMutation) {
    String sql = lockForMutation ? FIND_BY_ID_FOR_UPDATE_SQL : FIND_BY_ID_SQL;
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(sql)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, messageId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(mapMessage(resultSet)) : Optional.empty();
      }
    } catch (SQLException exception) {
      throw failure("find message", exception);
    }
  }

  @Override
  public long allocateSequence(ConversationId conversationId, Instant now) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(ALLOCATE_SEQUENCE_SQL)) {
      statement.setObject(1, toUtc(now));
      statement.setObject(2, conversationId.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new MessageExceptions.AccessDeniedException();
        }
        return resultSet.getLong(1);
      }
    } catch (SQLException exception) {
      throw failure("allocate message sequence", exception);
    }
  }

  @Override
  public void insert(Message message) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setObject(1, message.id().value());
      statement.setObject(2, message.conversationId().value());
      statement.setObject(3, message.senderId().value());
      statement.setObject(4, message.clientMessageId().value());
      statement.setLong(5, message.sequenceNumber());
      statement.setString(6, message.type().name());
      statement.setString(7, message.body().value());
      statement.setObject(8, toUtc(message.createdAt()));
      statement.setObject(9, null);
      statement.setObject(10, null);
      statement.executeUpdate();
    } catch (SQLException exception) {
      if (isUniqueViolation(exception) && mentions(exception, CLIENT_KEY_CONSTRAINT)) {
        throw new MessageExceptions.DuplicateClientMessageException(exception);
      }
      if (isUniqueViolation(exception) && mentions(exception, SEQUENCE_CONSTRAINT)) {
        throw new MessageExceptions.InternalException(
            "preserve the conversation message sequence invariant", exception);
      }
      throw failure("insert message", exception);
    }
  }

  @Override
  public List<Message> listAfter(
      ConversationId conversationId, UserId actorId, long afterSequence, int limit) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(LIST_AFTER_SQL)) {
      statement.setInt(1, limit);
      statement.setObject(2, actorId.value());
      statement.setObject(3, conversationId.value());
      statement.setLong(4, afterSequence);
      List<Message> messages = new ArrayList<>();
      try (var resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          messages.add(mapMessage(resultSet));
        }
      }
      return messages;
    } catch (SQLException exception) {
      throw failure("list conversation messages", exception);
    }
  }

  @Override
  public boolean edit(MessageId messageId, MessageBody body, Instant editedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(EDIT_SQL)) {
      statement.setString(1, body.value());
      statement.setObject(2, toUtc(editedAt));
      statement.setObject(3, messageId.value());
      return statement.executeUpdate() == 1;
    } catch (SQLException exception) {
      throw failure("edit message", exception);
    }
  }

  @Override
  public boolean softDelete(MessageId messageId, Instant deletedAt) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(DELETE_SQL)) {
      statement.setObject(1, toUtc(deletedAt));
      statement.setObject(2, messageId.value());
      return statement.executeUpdate() == 1;
    } catch (SQLException exception) {
      throw failure("soft-delete message", exception);
    }
  }

  private static Message mapMessage(ResultSet resultSet) throws SQLException {
    LocalDateTime editedAt = resultSet.getObject("edited_at", LocalDateTime.class);
    LocalDateTime deletedAt = resultSet.getObject("deleted_at", LocalDateTime.class);
    String body = resultSet.getString("body");
    return new Message(
        new MessageId(resultSet.getObject("id", UUID.class)),
        new ConversationId(resultSet.getObject("conversation_id", UUID.class)),
        new UserId(resultSet.getObject("sender_id", UUID.class)),
        new ClientMessageId(resultSet.getObject("client_message_id", UUID.class)),
        resultSet.getLong("sequence_number"),
        MessageType.valueOf(resultSet.getString("message_type")),
        body == null ? null : new MessageBody(body),
        fromUtc(resultSet.getObject("created_at", LocalDateTime.class)),
        editedAt == null ? null : fromUtc(editedAt),
        deletedAt == null ? null : fromUtc(deletedAt));
  }

  private static LocalDateTime toUtc(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant fromUtc(LocalDateTime value) {
    return value.toInstant(ZoneOffset.UTC);
  }

  private static RuntimeException failure(String operation, SQLException exception) {
    if (hasErrorCode(exception, DEADLOCK_VICTIM)) {
      return new MessageExceptions.DeadlockException(exception);
    }
    return new MessageExceptions.InternalException(operation, exception);
  }

  private static boolean isUniqueViolation(SQLException exception) {
    return hasErrorCode(exception, UNIQUE_INDEX) || hasErrorCode(exception, UNIQUE_CONSTRAINT);
  }

  private static boolean hasErrorCode(SQLException exception, int expected) {
    SQLException current = exception;
    while (current != null) {
      if (current.getErrorCode() == expected) {
        return true;
      }
      current = current.getNextException();
    }
    return false;
  }

  private static boolean mentions(SQLException exception, String constraint) {
    SQLException current = exception;
    String expected = constraint.toLowerCase(Locale.ROOT);
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
        return true;
      }
      current = current.getNextException();
    }
    return false;
  }
}
