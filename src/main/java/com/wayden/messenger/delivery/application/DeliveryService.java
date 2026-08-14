package com.wayden.messenger.delivery.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.domain.MessageDeliveryStatus;
import com.wayden.messenger.delivery.domain.MessagePosition;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.MessageId;

public interface DeliveryService {
  void acknowledgeDelivery(UserId actorId, ConversationId conversationId, Long sequence);

  void acknowledgeRead(UserId actorId, ConversationId conversationId, Long sequence);

  MessagePosition getPosition(UserId actorId, ConversationId conversationId);

  MessageDeliveryStatus getStatus(
      UserId actorId, ConversationId conversationId, MessageId messageId);
}
