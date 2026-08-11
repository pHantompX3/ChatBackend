package com.wayden.messenger.session.application;

import com.wayden.messenger.session.domain.Session;
import com.wayden.messenger.session.domain.SessionId;
import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {
  Session save(Session session);

  Optional<Session> findByTokenHash(byte[] tokenHash);

  boolean revoke(SessionId sessionId, Instant revokedAt);

  void touch(SessionId sessionId, Instant lastSeenAt);
}
