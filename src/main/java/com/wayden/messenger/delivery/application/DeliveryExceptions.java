package com.wayden.messenger.delivery.application;

public final class DeliveryExceptions {
  private DeliveryExceptions() {}

  public abstract static class DeliveryException extends RuntimeException {
    protected DeliveryException(String message) {
      super(message);
    }

    protected DeliveryException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class ValidationException extends DeliveryException {
    public ValidationException(String message) {
      super(message);
    }

    public ValidationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class SequenceAheadException extends DeliveryException {
    public SequenceAheadException() {
      super("The requested sequence exceeds committed conversation history");
    }
  }

  public static final class StatusForbiddenException extends DeliveryException {
    public StatusForbiddenException() {
      super("Only the message sender may inspect delivery status");
    }
  }

  public static final class ResourceNotFoundException extends DeliveryException {
    public ResourceNotFoundException() {
      super("The conversation or message does not exist or is not accessible");
    }
  }

  public static final class DeadlockException extends DeliveryException {
    public DeadlockException(Throwable cause) {
      super("The delivery transaction was selected as a deadlock victim", cause);
    }
  }

  public static final class InternalException extends DeliveryException {
    public InternalException(String operation, Throwable cause) {
      super("Failed to " + operation, cause);
    }
  }
}
