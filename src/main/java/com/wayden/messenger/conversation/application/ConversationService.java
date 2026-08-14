package com.wayden.messenger.conversation.application;

import com.wayden.messenger.conversation.application.ConversationRepository.ConversationView;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationMember;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.identity.domain.UserId;
import java.util.List;

public interface ConversationService {

  DirectResult createDirect(UserId actorUserId, UserId targetUserId);

  ConversationView createGroup(UserId actorUserId, String title, List<UserId> initialMemberIds);

  ConversationPage list(UserId actorUserId, String cursor, Integer limit);

  ConversationView get(UserId actorUserId, ConversationId conversationId);

  MemberPage listMembers(
      UserId actorUserId, ConversationId conversationId, String cursor, Integer limit);

  void addMember(UserId actorUserId, ConversationId conversationId, UserId targetUserId);

  void removeMember(UserId actorUserId, ConversationId conversationId, UserId targetUserId);

  void leave(UserId actorUserId, ConversationId conversationId);

  void changeRole(
      UserId actorUserId,
      ConversationId conversationId,
      UserId targetUserId,
      ConversationRole role);

  void transferOwnership(UserId actorUserId, ConversationId conversationId, UserId targetUserId);

  record DirectResult(ConversationView conversation, boolean created) {}

  record ConversationPage(List<ConversationView> items, String nextCursor) {

    public ConversationPage {
      items = List.copyOf(items);
    }
  }

  record MemberPage(List<ConversationMember> items, String nextCursor) {

    public MemberPage {
      items = List.copyOf(items);
    }
  }
}
