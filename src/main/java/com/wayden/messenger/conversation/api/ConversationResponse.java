package com.wayden.messenger.conversation.api;

import com.wayden.messenger.conversation.application.ConversationRepository.ConversationView;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID conversationId,
    String type,
    String title,
    UUID createdBy,
    String role,
    Instant createdAt,
    Instant updatedAt) {

  static ConversationResponse from(ConversationView view) {
    var conversation = view.conversation();
    return new ConversationResponse(
        conversation.id().value(),
        conversation.type().name(),
        conversation.title() == null ? null : conversation.title().value(),
        conversation.createdBy().value(),
        view.actorRole().name(),
        conversation.createdAt(),
        conversation.updatedAt());
  }
}
