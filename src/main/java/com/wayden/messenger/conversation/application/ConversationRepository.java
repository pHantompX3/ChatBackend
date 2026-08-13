package com.wayden.messenger.conversation.application;

import com.wayden.messenger.conversation.application.ConversationCursorCodec.ConversationCursor;
import com.wayden.messenger.conversation.domain.Conversation;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationMember;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.DirectParticipantPair;
import com.wayden.messenger.identity.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

  Optional<Conversation> findDirect(DirectParticipantPair pair, boolean lockForCreate);

  void createDirect(Conversation conversation, DirectParticipantPair pair, Instant joinedAt);

  void createGroup(
      Conversation conversation, UserId ownerId, List<UserId> initialMemberIds, Instant joinedAt);

  Optional<ConversationView> findAccessible(ConversationId conversationId, UserId actorUserId);

  List<ConversationView> listAccessible(UserId actorUserId, ConversationCursor after, int limit);

  List<ConversationMember> listActiveMembers(ConversationId conversationId);

  Optional<ConversationMember> findMembership(ConversationId conversationId, UserId userId);

  void addOrReactivateMember(ConversationId conversationId, UserId userId, Instant now);

  void markMemberLeft(ConversationId conversationId, UserId userId, Instant now);

  void changeRole(ConversationId conversationId, UserId userId, ConversationRole role, Instant now);

  void transferOwnership(
      ConversationId conversationId, UserId currentOwnerId, UserId newOwnerId, Instant now);

  record ConversationView(Conversation conversation, ConversationRole actorRole) {}
}
