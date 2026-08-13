package com.wayden.messenger.message.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageId;
import java.util.List;

public interface MessageService {

  SendResult send(
      UserId actorId, ConversationId conversationId, ClientMessageId clientMessageId, String body);

  MessagePage list(
      UserId actorId, ConversationId conversationId, Long afterSequence, Integer limit);

  Message edit(
      UserId actorId, ConversationId conversationId, MessageId messageId, String replacementBody);

  void delete(UserId actorId, ConversationId conversationId, MessageId messageId);

  record SendResult(Message message, boolean created) {}

  record MessagePage(List<Message> items, Long nextAfterSequence) {
    public MessagePage {
      items = List.copyOf(items);
    }
  }
}
