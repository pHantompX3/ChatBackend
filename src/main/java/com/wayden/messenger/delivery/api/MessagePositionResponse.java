package com.wayden.messenger.delivery.api;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.domain.MessagePosition;
import java.util.UUID;

public record MessagePositionResponse(
    UUID conversationId,
    long latestSequence,
    long lastDeliveredSequence,
    long lastReadSequence,
    long unreadCount) {

  static MessagePositionResponse from(ConversationId conversationId, MessagePosition position) {
    return new MessagePositionResponse(
        conversationId.value(),
        position.latestSequence(),
        position.lastDeliveredSequence(),
        position.lastReadSequence(),
        position.unreadCount());
  }
}
