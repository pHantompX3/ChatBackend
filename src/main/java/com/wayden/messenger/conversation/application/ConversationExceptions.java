package com.wayden.messenger.conversation.application;

public final class ConversationExceptions {
  private ConversationExceptions() {}

  public abstract static class ConversationException extends RuntimeException {
    protected ConversationException(String message) {
      super(message);
    }

    protected ConversationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class ValidationException extends ConversationException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static final class UserSearchValidationException extends ConversationException {
    public UserSearchValidationException(String message) {
      super(message);
    }
  }

  public static final class AccessDeniedException extends ConversationException {
    public AccessDeniedException() {
      super("The conversation does not exist or is not accessible");
    }
  }

  public static final class RoleForbiddenException extends ConversationException {
    public RoleForbiddenException(String message) {
      super(message);
    }
  }

  public static final class OwnershipRequiredException extends ConversationException {
    public OwnershipRequiredException(String message) {
      super(message);
    }
  }

  public static final class UserNotFoundException extends ConversationException {
    public UserNotFoundException() {
      super("User was not found");
    }
  }

  public static final class InvalidCursorException extends ConversationException {
    public InvalidCursorException() {
      super("Cursor is invalid or does not match the request");
    }

    public InvalidCursorException(Throwable cause) {
      super("Cursor is invalid or does not match the request", cause);
    }
  }

  public static final class DuplicateDirectPairException extends ConversationException {
    public DuplicateDirectPairException(Throwable cause) {
      super("Direct conversation pair already exists", cause);
    }
  }

  public static final class InternalException extends ConversationException {
    public InternalException(String operation, Throwable cause) {
      super("Failed to " + operation, cause);
    }
  }
}
