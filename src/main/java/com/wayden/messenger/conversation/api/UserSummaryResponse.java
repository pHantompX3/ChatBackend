package com.wayden.messenger.conversation.api;

import com.wayden.messenger.conversation.application.UserDirectoryService.UserSummary;
import java.util.UUID;

public record UserSummaryResponse(UUID userId, String username) {
  static UserSummaryResponse from(UserSummary user) {
    return new UserSummaryResponse(user.userId().value(), user.username());
  }
}
