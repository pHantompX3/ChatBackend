package com.wayden.messenger.conversation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.conversation.application.ConversationCursorCodec.MemberCursor;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationCursorCodecTest {

  private final ConversationCursorCodec codec = new ConversationCursorCodec(new ObjectMapper());

  @Test
  void memberCursorRoundTripsOnlyForItsConversation() {
    ConversationId conversationId = new ConversationId(UUID.randomUUID());
    MemberCursor expected =
        new MemberCursor(
            conversationId, Instant.parse("2026-08-14T12:00:00Z"), new UserId(UUID.randomUUID()));
    String encoded = codec.encodeMember(expected);

    assertEquals(expected, codec.decodeMember(encoded, conversationId));
    assertThrows(
        ConversationExceptions.InvalidCursorException.class,
        () -> codec.decodeMember(encoded, new ConversationId(UUID.randomUUID())));
  }
}
