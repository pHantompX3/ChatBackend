package com.wayden.messenger.delivery.domain;

public record AcknowledgementResult(
    long latestSequence,
    long previousDeliveredSequence,
    long currentDeliveredSequence,
    long previousReadSequence,
    long currentReadSequence,
    Outcome outcome) {

  public AcknowledgementResult {
    if (latestSequence < 0
        || previousDeliveredSequence < 0
        || currentDeliveredSequence < 0
        || previousReadSequence < 0
        || currentReadSequence < 0) {
      throw new IllegalArgumentException("Acknowledgement positions must not be negative");
    }
    if (previousReadSequence > previousDeliveredSequence
        || currentReadSequence > currentDeliveredSequence
        || currentDeliveredSequence > latestSequence) {
      throw new IllegalArgumentException("Acknowledgement positions are inconsistent");
    }
    if (currentDeliveredSequence < previousDeliveredSequence
        || currentReadSequence < previousReadSequence) {
      throw new IllegalArgumentException("Acknowledgement positions must be monotonic");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("Acknowledgement outcome must not be null");
    }
    boolean changed =
        currentDeliveredSequence != previousDeliveredSequence
            || currentReadSequence != previousReadSequence;
    if (changed != (outcome == Outcome.ADVANCED)) {
      throw new IllegalArgumentException("Acknowledgement outcome does not match its positions");
    }
  }

  public enum Outcome {
    ADVANCED,
    UNCHANGED
  }
}
