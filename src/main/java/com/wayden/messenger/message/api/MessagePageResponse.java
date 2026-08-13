package com.wayden.messenger.message.api;

import java.util.List;

public record MessagePageResponse(List<MessageResponse> items, Long nextAfterSequence) {
  public MessagePageResponse {
    items = List.copyOf(items);
  }
}
