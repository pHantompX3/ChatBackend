package com.wayden.messenger.session.application;

import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.domain.SessionId;

public final class SessionEvents {

  private SessionEvents() {}

  public record SessionRevokedEvent(UserId userId, SessionId sessionId) {}

  public record AllSessionsRevokedEvent(UserId userId) {}
}
