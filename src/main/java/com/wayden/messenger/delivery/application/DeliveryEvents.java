package com.wayden.messenger.delivery.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;

public final class DeliveryEvents {

  private DeliveryEvents() {}

  public record DeliveryAcknowledgedEvent(
      ConversationId conversationId,
      UserId userId,
      long lastDeliveredSequence,
      Instant updatedAt) {}

  public record ReadAcknowledgedEvent(
      ConversationId conversationId,
      UserId userId,
      long lastReadSequence,
      long lastDeliveredSequence,
      Instant updatedAt) {}
}
