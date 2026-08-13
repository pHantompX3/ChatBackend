package com.wayden.messenger.conversation.api;

import com.wayden.messenger.conversation.domain.ConversationMember;
import java.time.Instant;
import java.util.UUID;

public record ConversationMemberResponse(
    UUID userId, String username, String role, Instant joinedAt) {

  static ConversationMemberResponse from(ConversationMember member) {
    return new ConversationMemberResponse(
        member.userId().value(), member.username(), member.role().name(), member.joinedAt());
  }
}
