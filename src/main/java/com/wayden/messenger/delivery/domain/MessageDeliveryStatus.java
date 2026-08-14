package com.wayden.messenger.delivery.domain;

import com.wayden.messenger.message.domain.MessageId;
import java.util.Objects;

public record MessageDeliveryStatus(
    MessageId messageId,
    long sequence,
    boolean serverAccepted,
    long recipientCount,
    long deliveredCount,
    long readCount,
    boolean allDelivered,
    boolean allRead) {

  public MessageDeliveryStatus {
    Objects.requireNonNull(messageId, "messageId must not be null");
    if (sequence < 0 || recipientCount < 0 || deliveredCount < 0 || readCount < 0) {
      throw new IllegalArgumentException("Delivery status values must not be negative");
    }
    if (!serverAccepted) {
      throw new IllegalArgumentException("Persisted message status must be server accepted");
    }
    if (deliveredCount > recipientCount || readCount > deliveredCount) {
      throw new IllegalArgumentException("Delivery status counts are inconsistent");
    }
    boolean expectedAllDelivered = recipientCount > 0 && deliveredCount == recipientCount;
    boolean expectedAllRead = recipientCount > 0 && readCount == recipientCount;
    if (allDelivered != expectedAllDelivered || allRead != expectedAllRead) {
      throw new IllegalArgumentException("Delivery status aggregate flags are inconsistent");
    }
  }
}
