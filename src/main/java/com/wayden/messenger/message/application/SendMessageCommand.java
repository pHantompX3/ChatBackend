package com.wayden.messenger.message.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.MessageBody;
import java.util.Objects;

public record SendMessageCommand(
    UserId senderId,
    ConversationId conversationId,
    ClientMessageId clientMessageId,
    MessageBody body) {
  public SendMessageCommand {
    Objects.requireNonNull(senderId, "Sender ID must not be null");
    Objects.requireNonNull(conversationId, "Conversation ID must not be null");
    Objects.requireNonNull(clientMessageId, "Client message ID must not be null");
    Objects.requireNonNull(body, "Message body must not be null");
  }
}
