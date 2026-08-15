package com.wayden.messenger.message.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageId;
import java.time.Instant;

public final class MessageEvents {

  private MessageEvents() {}

  public record MessageCreatedEvent(
      ConversationId conversationId,
      MessageId messageId,
      long sequenceNumber,
      UserId senderId,
      ClientMessageId clientMessageId,
      String body,
      Instant createdAt) {

    public static MessageCreatedEvent from(Message message) {
      return new MessageCreatedEvent(
          message.conversationId(),
          message.id(),
          message.sequenceNumber(),
          message.senderId(),
          message.clientMessageId(),
          message.body().value(),
          message.createdAt());
    }
  }

  public record MessageEditedEvent(
      ConversationId conversationId,
      MessageId messageId,
      long sequenceNumber,
      String body,
      Instant editedAt) {

    public static MessageEditedEvent from(Message message) {
      return new MessageEditedEvent(
          message.conversationId(),
          message.id(),
          message.sequenceNumber(),
          message.body().value(),
          message.editedAt());
    }
  }

  public record MessageDeletedEvent(
      ConversationId conversationId,
      MessageId messageId,
      long sequenceNumber,
      Instant deletedAt) {

    public static MessageDeletedEvent from(Message message) {
      return new MessageDeletedEvent(
          message.conversationId(),
          message.id(),
          message.sequenceNumber(),
          message.deletedAt());
    }
  }
}
