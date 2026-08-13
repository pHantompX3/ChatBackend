package com.wayden.messenger.message.application;

public final class MessageExceptions {
  private MessageExceptions() {}

  public abstract static class MessageException extends RuntimeException {
    protected MessageException(String message) {
      super(message);
    }

    protected MessageException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class ValidationException extends MessageException {
    public ValidationException(String message) {
      super(message);
    }

    public ValidationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class AccessDeniedException extends MessageException {
    public AccessDeniedException() {
      super("The conversation or message does not exist or is not accessible");
    }
  }

  public static final class EditForbiddenException extends MessageException {
    public EditForbiddenException() {
      super("The message cannot be edited by this actor");
    }
  }

  public static final class DeleteForbiddenException extends MessageException {
    public DeleteForbiddenException() {
      super("The message cannot be deleted by this actor");
    }
  }

  public static final class IdempotencyConflictException extends MessageException {
    public IdempotencyConflictException() {
      super("The client message ID is already used for another conversation");
    }
  }

  public static final class DuplicateClientMessageException extends MessageException {
    public DuplicateClientMessageException(Throwable cause) {
      super("A concurrent request accepted this client message ID", cause);
    }
  }

  public static final class DeadlockException extends MessageException {
    public DeadlockException(Throwable cause) {
      super("The message transaction was selected as a deadlock victim", cause);
    }
  }

  public static final class InternalException extends MessageException {
    public InternalException(String operation, Throwable cause) {
      super("Failed to " + operation, cause);
    }
  }
}
