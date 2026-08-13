package com.wayden.messenger.message.domain;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.Objects;

public record Message(
    MessageId id,
    ConversationId conversationId,
    UserId senderId,
    ClientMessageId clientMessageId,
    long sequenceNumber,
    MessageType type,
    MessageBody body,
    Instant createdAt,
    Instant editedAt,
    Instant deletedAt) {

  public Message {
    Objects.requireNonNull(id, "Message ID must not be null");
    Objects.requireNonNull(conversationId, "Conversation ID must not be null");
    Objects.requireNonNull(senderId, "Sender ID must not be null");
    Objects.requireNonNull(clientMessageId, "Client message ID must not be null");
    Objects.requireNonNull(type, "Message type must not be null");
    Objects.requireNonNull(createdAt, "Creation timestamp must not be null");
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentException("Message sequence must be positive");
    }
    if (deletedAt == null && body == null) {
      throw new IllegalArgumentException("An active message must have a body");
    }
    if (deletedAt != null && body != null) {
      throw new IllegalArgumentException("A deleted message must be a bodyless tombstone");
    }
    if (editedAt != null && editedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("Edit timestamp must not precede creation");
    }
    Instant mutationFloor = editedAt == null ? createdAt : editedAt;
    if (deletedAt != null && deletedAt.isBefore(mutationFloor)) {
      throw new IllegalArgumentException("Deletion timestamp must not precede message activity");
    }
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public Message edit(MessageBody replacement, Instant now) {
    Objects.requireNonNull(replacement, "Replacement body must not be null");
    Objects.requireNonNull(now, "Edit timestamp must not be null");
    if (type != MessageType.TEXT || isDeleted()) {
      throw new IllegalStateException("Only active text messages can be edited");
    }
    if (body.equals(replacement)) {
      return this;
    }
    return new Message(
        id,
        conversationId,
        senderId,
        clientMessageId,
        sequenceNumber,
        type,
        replacement,
        createdAt,
        now,
        null);
  }

  public Message delete(Instant now) {
    Objects.requireNonNull(now, "Deletion timestamp must not be null");
    if (isDeleted()) {
      return this;
    }
    return new Message(
        id,
        conversationId,
        senderId,
        clientMessageId,
        sequenceNumber,
        type,
        null,
        createdAt,
        editedAt,
        now);
  }
}
