package com.wayden.messenger.delivery.infrastructure;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryExceptions;
import com.wayden.messenger.delivery.application.DeliveryRepository;
import com.wayden.messenger.delivery.domain.AcknowledgementResult;
import com.wayden.messenger.delivery.domain.AcknowledgementResult.Outcome;
import com.wayden.messenger.delivery.domain.MessageDeliveryStatus;
import com.wayden.messenger.delivery.domain.MessagePosition;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.MessageId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class JdbcDeliveryRepository implements DeliveryRepository {

  private static final int DEADLOCK_VICTIM = 1205;
  private static final String LOCK_MEMBERSHIP_SQL =
      "SELECT 1 "
          + "FROM [messaging].[conversation_member] WITH (UPDLOCK) "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
  private static final String LOCK_HIGH_WATER_SQL =
      "SELECT next_message_sequence - 1 AS latest_sequence "
          + "FROM [messaging].[conversation] WITH (UPDLOCK) WHERE id = ?";
  private static final String ACKNOWLEDGE_DELIVERY_SQL =
      "UPDATE [messaging].[conversation_member] "
          + "SET last_delivered_sequence = CASE WHEN last_delivered_sequence < ? THEN ? "
          + "ELSE last_delivered_sequence END "
          + "OUTPUT DELETED.last_delivered_sequence, INSERTED.last_delivered_sequence, "
          + "DELETED.last_read_sequence, INSERTED.last_read_sequence "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
  private static final String ACKNOWLEDGE_READ_SQL =
      "UPDATE [messaging].[conversation_member] "
          + "SET last_delivered_sequence = CASE WHEN last_delivered_sequence < ? THEN ? "
          + "ELSE last_delivered_sequence END, "
          + "last_read_sequence = CASE WHEN last_read_sequence < ? THEN ? "
          + "ELSE last_read_sequence END "
          + "OUTPUT DELETED.last_delivered_sequence, INSERTED.last_delivered_sequence, "
          + "DELETED.last_read_sequence, INSERTED.last_read_sequence "
          + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
  private static final String FIND_POSITION_SQL =
      "SELECT c.next_message_sequence - 1 AS latest_sequence, "
          + "actor.last_delivered_sequence, actor.last_read_sequence, "
          + "(SELECT COUNT_BIG(*) FROM [messaging].[message] m "
          + "WHERE m.conversation_id = c.id "
          + "AND m.sequence_number > actor.last_read_sequence "
          + "AND m.sequence_number <= c.next_message_sequence - 1 "
          + "AND m.sender_id <> actor.user_id AND m.deleted_at IS NULL) AS unread_count "
          + "FROM [messaging].[conversation_member] actor "
          + "JOIN [messaging].[conversation] c ON c.id = actor.conversation_id "
          + "WHERE actor.conversation_id = ? AND actor.user_id = ? AND actor.left_at IS NULL";
  private static final String RESOLVE_STATUS_ACCESS_SQL =
      "SELECT m.sender_id, m.sequence_number "
          + "FROM [messaging].[conversation_member] actor WITH (REPEATABLEREAD) "
          + "LEFT JOIN [messaging].[message] m "
          + "ON m.conversation_id = actor.conversation_id AND m.id = ? "
          + "WHERE actor.conversation_id = ? AND actor.user_id = ? AND actor.left_at IS NULL";
  private static final String AGGREGATE_STATUS_SQL =
      "SELECT COUNT_BIG(*) AS recipient_count, "
          + "COALESCE(SUM(CONVERT(BIGINT, CASE WHEN last_delivered_sequence >= ? "
          + "THEN 1 ELSE 0 END)), 0) AS delivered_count, "
          + "COALESCE(SUM(CONVERT(BIGINT, CASE WHEN last_read_sequence >= ? "
          + "THEN 1 ELSE 0 END)), 0) AS read_count "
          + "FROM [messaging].[conversation_member] "
          + "WHERE conversation_id = ? AND user_id <> ? AND left_at IS NULL";

  private final DataSource dataSource;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "DataSource is container-managed and retained for JDBC operations.")
  public JdbcDeliveryRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public AcknowledgementAttempt acknowledgeDelivery(
      ConversationId conversationId, UserId actorId, long sequence) {
    return acknowledge(conversationId, actorId, sequence, false);
  }

  @Override
  public AcknowledgementAttempt acknowledgeRead(
      ConversationId conversationId, UserId actorId, long sequence) {
    return acknowledge(conversationId, actorId, sequence, true);
  }

  @Override
  public Optional<MessagePosition> findPosition(ConversationId conversationId, UserId actorId) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(FIND_POSITION_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, actorId.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        try {
          return Optional.of(
              new MessagePosition(
                  resultSet.getLong("latest_sequence"),
                  resultSet.getLong("last_delivered_sequence"),
                  resultSet.getLong("last_read_sequence"),
                  resultSet.getLong("unread_count")));
        } catch (IllegalArgumentException exception) {
          throw new DeliveryExceptions.InternalException(
              "map the persisted conversation position", exception);
        }
      }
    } catch (SQLException exception) {
      throw failure("query the conversation position", exception);
    }
  }

  @Override
  public StatusLookup findSenderStatus(
      ConversationId conversationId, MessageId messageId, UserId actorId) {
    try (var connection = dataSource.getConnection()) {
      StatusSubject subject = resolveStatusSubject(connection, conversationId, messageId, actorId);
      if (subject == null) {
        return new StatusLookup.ResourceNotFound();
      }
      if (!subject.senderId().equals(actorId.value())) {
        return new StatusLookup.Forbidden();
      }
      try (var statement = connection.prepareStatement(AGGREGATE_STATUS_SQL)) {
        statement.setLong(1, subject.sequence());
        statement.setLong(2, subject.sequence());
        statement.setObject(3, conversationId.value());
        statement.setObject(4, actorId.value());
        try (var resultSet = statement.executeQuery()) {
          if (!resultSet.next()) {
            throw invariant("Delivery status aggregate returned no row");
          }
          long recipientCount = resultSet.getLong("recipient_count");
          long deliveredCount = resultSet.getLong("delivered_count");
          long readCount = resultSet.getLong("read_count");
          return new StatusLookup.Found(
              new MessageDeliveryStatus(
                  messageId,
                  subject.sequence(),
                  true,
                  recipientCount,
                  deliveredCount,
                  readCount,
                  recipientCount > 0 && deliveredCount == recipientCount,
                  recipientCount > 0 && readCount == recipientCount));
        }
      }
    } catch (SQLException exception) {
      throw failure("query message delivery status", exception);
    } catch (IllegalArgumentException exception) {
      throw new DeliveryExceptions.InternalException(
          "map persisted message delivery status", exception);
    }
  }

  private AcknowledgementAttempt acknowledge(
      ConversationId conversationId, UserId actorId, long sequence, boolean read) {
    try (var connection = dataSource.getConnection()) {
      if (!lockMembership(connection, conversationId, actorId)) {
        return new AcknowledgementAttempt.ResourceNotFound();
      }
      Long latestSequence = lockHighWater(connection, conversationId);
      if (latestSequence == null) {
        throw invariant("Active membership references a missing conversation");
      }
      if (sequence > latestSequence) {
        return new AcknowledgementAttempt.SequenceAhead(latestSequence);
      }
      return new AcknowledgementAttempt.Acknowledged(
          updatePosition(connection, conversationId, actorId, sequence, latestSequence, read));
    } catch (SQLException exception) {
      throw failure("acknowledge the conversation position", exception);
    }
  }

  private static boolean lockMembership(
      java.sql.Connection connection, ConversationId conversationId, UserId actorId)
      throws SQLException {
    try (var statement = connection.prepareStatement(LOCK_MEMBERSHIP_SQL)) {
      statement.setObject(1, conversationId.value());
      statement.setObject(2, actorId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static Long lockHighWater(java.sql.Connection connection, ConversationId conversationId)
      throws SQLException {
    try (var statement = connection.prepareStatement(LOCK_HIGH_WATER_SQL)) {
      statement.setObject(1, conversationId.value());
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getLong("latest_sequence") : null;
      }
    }
  }

  private static AcknowledgementResult updatePosition(
      java.sql.Connection connection,
      ConversationId conversationId,
      UserId actorId,
      long sequence,
      long latestSequence,
      boolean read)
      throws SQLException {
    String sql = read ? ACKNOWLEDGE_READ_SQL : ACKNOWLEDGE_DELIVERY_SQL;
    try (var statement = connection.prepareStatement(sql)) {
      int parameter = 1;
      statement.setLong(parameter++, sequence);
      statement.setLong(parameter++, sequence);
      if (read) {
        statement.setLong(parameter++, sequence);
        statement.setLong(parameter++, sequence);
      }
      statement.setObject(parameter++, conversationId.value());
      statement.setObject(parameter, actorId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw invariant("Locked active membership was not updated");
        }
        long previousDelivered = resultSet.getLong(1);
        long currentDelivered = resultSet.getLong(2);
        long previousRead = resultSet.getLong(3);
        long currentRead = resultSet.getLong(4);
        Outcome outcome =
            previousDelivered == currentDelivered && previousRead == currentRead
                ? Outcome.UNCHANGED
                : Outcome.ADVANCED;
        try {
          return new AcknowledgementResult(
              latestSequence,
              previousDelivered,
              currentDelivered,
              previousRead,
              currentRead,
              outcome);
        } catch (IllegalArgumentException exception) {
          throw new DeliveryExceptions.InternalException(
              "map the persisted acknowledgement result", exception);
        }
      }
    }
  }

  private static StatusSubject resolveStatusSubject(
      java.sql.Connection connection,
      ConversationId conversationId,
      MessageId messageId,
      UserId actorId)
      throws SQLException {
    try (var statement = connection.prepareStatement(RESOLVE_STATUS_ACCESS_SQL)) {
      statement.setObject(1, messageId.value());
      statement.setObject(2, conversationId.value());
      statement.setObject(3, actorId.value());
      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        UUID senderId = resultSet.getObject("sender_id", UUID.class);
        return senderId == null
            ? null
            : new StatusSubject(senderId, resultSet.getLong("sequence_number"));
      }
    }
  }

  private static RuntimeException failure(String operation, SQLException exception) {
    if (hasErrorCode(exception, DEADLOCK_VICTIM)) {
      return new DeliveryExceptions.DeadlockException(exception);
    }
    return new DeliveryExceptions.InternalException(operation, exception);
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

  private static DeliveryExceptions.InternalException invariant(String message) {
    return new DeliveryExceptions.InternalException(
        "preserve delivery persistence invariants", new IllegalStateException(message));
  }

  private record StatusSubject(UUID senderId, long sequence) {}
}
