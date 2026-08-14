package com.wayden.messenger.delivery.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.domain.AcknowledgementResult;
import com.wayden.messenger.delivery.domain.MessageDeliveryStatus;
import com.wayden.messenger.delivery.domain.MessagePosition;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.MessageId;

public interface DeliveryRepository {

  AcknowledgementAttempt acknowledgeDelivery(
      ConversationId conversationId, UserId actorId, long sequence);

  AcknowledgementAttempt acknowledgeRead(
      ConversationId conversationId, UserId actorId, long sequence);

  PositionLookup findPosition(ConversationId conversationId, UserId actorId);

  StatusLookup findSenderStatus(ConversationId conversationId, MessageId messageId, UserId actorId);

  sealed interface AcknowledgementAttempt {
    record Acknowledged(AcknowledgementResult result) implements AcknowledgementAttempt {}

    record ResourceNotFound() implements AcknowledgementAttempt {}

    record SequenceAhead(long latestSequence) implements AcknowledgementAttempt {}
  }

  sealed interface PositionLookup {
    record Found(MessagePosition position) implements PositionLookup {}

    record ResourceNotFound() implements PositionLookup {}
  }

  sealed interface StatusLookup {
    record Found(MessageDeliveryStatus status) implements StatusLookup {}

    record ResourceNotFound() implements StatusLookup {}

    record Forbidden() implements StatusLookup {}
  }
}
