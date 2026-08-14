package com.wayden.messenger.delivery.api;

import com.wayden.messenger.delivery.domain.MessageDeliveryStatus;
import java.util.UUID;

public record MessageDeliveryStatusResponse(
    UUID messageId,
    long sequence,
    boolean serverAccepted,
    long recipientCount,
    long deliveredCount,
    long readCount,
    boolean allDelivered,
    boolean allRead) {

  static MessageDeliveryStatusResponse from(MessageDeliveryStatus status) {
    return new MessageDeliveryStatusResponse(
        status.messageId().value(),
        status.sequence(),
        status.serverAccepted(),
        status.recipientCount(),
        status.deliveredCount(),
        status.readCount(),
        status.allDelivered(),
        status.allRead());
  }
}
