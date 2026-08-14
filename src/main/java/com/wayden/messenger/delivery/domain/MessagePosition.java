package com.wayden.messenger.delivery.domain;

public record MessagePosition(
    long latestSequence, long lastDeliveredSequence, long lastReadSequence, long unreadCount) {

  public MessagePosition {
    if (latestSequence < 0
        || lastDeliveredSequence < 0
        || lastReadSequence < 0
        || unreadCount < 0) {
      throw new IllegalArgumentException("Message positions must not be negative");
    }
    if (lastReadSequence > lastDeliveredSequence) {
      throw new IllegalArgumentException("Read position must not exceed delivery position");
    }
    if (lastDeliveredSequence > latestSequence) {
      throw new IllegalArgumentException("Delivery position must not exceed latest sequence");
    }
  }
}
