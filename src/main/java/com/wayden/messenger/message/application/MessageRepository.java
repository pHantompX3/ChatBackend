package com.wayden.messenger.message.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.ConversationType;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.Message;
import com.wayden.messenger.message.domain.MessageBody;
import com.wayden.messenger.message.domain.MessageId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository {

  Optional<ActorAccess> findActiveAccess(
      ConversationId conversationId, UserId actorId, boolean lockForMutation);

  Optional<Message> findByClientMessageId(UserId senderId, ClientMessageId clientMessageId);

  Optional<Message> findById(
      ConversationId conversationId, MessageId messageId, boolean lockForMutation);

  long allocateSequence(ConversationId conversationId, Instant now);

  void insert(Message message);

  List<Message> listAfter(
      ConversationId conversationId, UserId actorId, long afterSequence, int limit);

  boolean edit(MessageId messageId, MessageBody body, Instant editedAt);

  boolean softDelete(MessageId messageId, Instant deletedAt);

  record ActorAccess(ConversationType conversationType, ConversationRole role) {}
}
