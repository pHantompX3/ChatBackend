package com.wayden.messenger.message.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MessageTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-13T18:00:00Z");

  @Test
  void textMessageShouldPreserveSubmittedWhitespace() {
    Message message = message(new MessageBody("  hello  "));

    assertEquals("  hello  ", message.body().value());
    assertEquals(1, message.sequenceNumber());
  }

  @Test
  void bodyShouldRejectNullBlankAndOversizedValues() {
    assertThrows(IllegalArgumentException.class, () -> new MessageBody(null));
    assertThrows(IllegalArgumentException.class, () -> new MessageBody(" \t\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MessageBody("x".repeat(MessageBody.MAX_LENGTH + 1)));
  }

  @Test
  void messageShouldEnforceSequenceAndTimestampOrdering() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Message(
                id(),
                conversationId(),
                userId(),
                clientId(),
                0,
                MessageType.TEXT,
                new MessageBody("hello"),
                CREATED_AT,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Message(
                id(),
                conversationId(),
                userId(),
                clientId(),
                1,
                MessageType.TEXT,
                new MessageBody("hello"),
                CREATED_AT,
                CREATED_AT.minusSeconds(1),
                null));
  }

  @Test
  void deletionShouldCreateAStableTombstoneAndPreventEditing() {
    Instant deletedAt = CREATED_AT.plusSeconds(5);
    Message deleted = message(new MessageBody("hello")).delete(deletedAt);

    assertNull(deleted.body());
    assertEquals(deletedAt, deleted.deletedAt());
    assertEquals(deleted, deleted.delete(deletedAt.plusSeconds(1)));
    assertThrows(
        IllegalStateException.class,
        () -> deleted.edit(new MessageBody("replacement"), deletedAt.plusSeconds(2)));
  }

  @Test
  void systemMessagesShouldNotBeEditable() {
    Message system =
        new Message(
            id(),
            conversationId(),
            userId(),
            clientId(),
            1,
            MessageType.SYSTEM,
            new MessageBody("system event"),
            CREATED_AT,
            null,
            null);

    assertThrows(
        IllegalStateException.class,
        () -> system.edit(new MessageBody("replacement"), CREATED_AT.plusSeconds(1)));
  }

  private static Message message(MessageBody body) {
    return new Message(
        id(),
        conversationId(),
        userId(),
        clientId(),
        1,
        MessageType.TEXT,
        body,
        CREATED_AT,
        null,
        null);
  }

  private static MessageId id() {
    return new MessageId(UUID.randomUUID());
  }

  private static ConversationId conversationId() {
    return new ConversationId(UUID.randomUUID());
  }

  private static UserId userId() {
    return new UserId(UUID.randomUUID());
  }

  private static ClientMessageId clientId() {
    return new ClientMessageId(UUID.randomUUID());
  }
}
