package com.wayden.messenger.session.application;

import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.domain.Session;

public interface SessionService {
  LoginResult login(LoginCommand command);

  void logout(LogoutCommand command);

  int revokeAllSessionsForUser(RevokeAllSessionsCommand command);

  Session resolveActiveSession(String rawToken);

  User resolveAuthenticatedUser(String rawToken);

  record LoginCommand(String username, String password, String userAgent, String sourceAddress) {}

  record LoginResult(String sessionId, String token, User user) {}

  record LogoutCommand(String rawToken) {}

  record RevokeAllSessionsCommand(UserId targetUserId) {}
}
