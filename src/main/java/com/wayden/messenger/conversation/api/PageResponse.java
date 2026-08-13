package com.wayden.messenger.conversation.api;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor) {

  public PageResponse {
    items = List.copyOf(items);
  }
}
