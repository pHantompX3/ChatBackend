package com.wayden.messenger.common.api;

public final class PaginationPolicy {

  public static final int MAX_CURSOR_LENGTH = 1024;

  private PaginationPolicy() {}

  public static int resolveLimit(Integer requested, int defaultLimit, int maximumLimit) {
    if (requested == null) {
      return defaultLimit;
    }
    if (requested < 1 || requested > maximumLimit) {
      throw new IllegalArgumentException("limit must be between 1 and " + maximumLimit);
    }
    return requested;
  }

  public static void requireValidCursorLength(String cursor) {
    if (cursor != null && cursor.length() > MAX_CURSOR_LENGTH) {
      throw new IllegalArgumentException("cursor exceeds the maximum length");
    }
  }
}
