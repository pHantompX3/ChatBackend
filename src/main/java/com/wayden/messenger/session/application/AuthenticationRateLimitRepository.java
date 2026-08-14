package com.wayden.messenger.session.application;

import java.time.Duration;

public interface AuthenticationRateLimitRepository {

  Decision reserve(
      byte[] accountHash,
      byte[] sourceHash,
      int accountLimit,
      Duration accountWindow,
      int sourceLimit,
      Duration sourceWindow);

  record Decision(boolean allowed, String exhaustedScope, long retryAfterSeconds) {

    public static Decision permitted() {
      return new Decision(true, null, 0);
    }

    public static Decision rejected(String scope, long retryAfterSeconds) {
      return new Decision(false, scope, Math.max(1, retryAfterSeconds));
    }
  }
}
