package com.wayden.messenger.conversation.domain;

import com.wayden.messenger.identity.domain.UserId;

public record DirectParticipantPair(UserId low, UserId high) {
  public DirectParticipantPair {
    if (low == null || high == null) {
      throw new IllegalArgumentException("Direct participants must not be null");
    }
    if (low.equals(high)) {
      throw new IllegalArgumentException("A direct conversation requires two distinct users");
    }
    if (compare(low, high) >= 0) {
      throw new IllegalArgumentException("Direct participants must be in canonical order");
    }
  }

  public static DirectParticipantPair of(UserId first, UserId second) {
    if (first == null || second == null) {
      throw new IllegalArgumentException("Direct participants must not be null");
    }
    return compare(first, second) < 0
        ? new DirectParticipantPair(first, second)
        : new DirectParticipantPair(second, first);
  }

  private static int compare(UserId first, UserId second) {
    return first.value().toString().compareTo(second.value().toString());
  }
}
