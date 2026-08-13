package com.wayden.messenger.conversation.api;

import java.util.List;

public record CreateGroupConversationRequest(String title, List<String> initialMemberIds) {

  public CreateGroupConversationRequest {
    initialMemberIds = initialMemberIds == null ? List.of() : List.copyOf(initialMemberIds);
  }
}
