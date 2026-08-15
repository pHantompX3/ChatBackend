package com.wayden.messenger.realtime.domain;

public enum RealtimeEventType {
  MESSAGE_CREATED("message.created"),
  MESSAGE_EDITED("message.edited"),
  MESSAGE_DELETED("message.deleted"),
  DELIVERY_UPDATED("delivery.updated"),
  READ_UPDATED("read.updated");

  private final String typeName;

  RealtimeEventType(String typeName) {
    this.typeName = typeName;
  }

  public String typeName() {
    return typeName;
  }
}
