package com.wayden.messenger.conversation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.identity.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DirectParticipantPairTest {

  @Test
  void shouldCanonicalizeByLowercaseUuidStringOrder() {
    UserId first = new UserId(UUID.fromString("00000000-0000-0000-8000-000000000000"));
    UserId second = new UserId(UUID.fromString("00000000-0000-0000-7fff-ffffffffffff"));

    DirectParticipantPair forward = DirectParticipantPair.of(first, second);
    DirectParticipantPair reverse = DirectParticipantPair.of(second, first);

    assertEquals(second, forward.low());
    assertEquals(first, forward.high());
    assertEquals(forward, reverse);
  }

  @Test
  void shouldRejectSelfConversation() {
    UserId userId = UserId.newId();
    assertThrows(IllegalArgumentException.class, () -> DirectParticipantPair.of(userId, userId));
  }
}
