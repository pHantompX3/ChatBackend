package com.wayden.messenger.session.application;

public final class SessionExceptions {

  private SessionExceptions() {}

  public static class SessionException extends RuntimeException {
    protected SessionException(String message) {
      super(message);
    }
  }

  public static final class InvalidCredentialsException extends SessionException {
    public InvalidCredentialsException() {
      super("Invalid username or password");
    }
  }

  public static final class DisabledUserException extends SessionException {
    public DisabledUserException() {
      super("User is disabled");
    }
  }

  public static final class MissingTokenException extends SessionException {
    public MissingTokenException() {
      super("Authorization token is required");
    }
  }

  public static final class InvalidSessionException extends SessionException {
    public InvalidSessionException() {
      super("Session is invalid");
    }
  }

  public static final class RevokedSessionException extends SessionException {
    public RevokedSessionException() {
      super("Session has been revoked");
    }
  }

  public static final class ExpiredSessionException extends SessionException {
    public ExpiredSessionException() {
      super("Session has expired");
    }
  }

  public static final class SessionUserNotFoundException extends SessionException {
    public SessionUserNotFoundException() {
      super("User was not found");
    }
  }
}
