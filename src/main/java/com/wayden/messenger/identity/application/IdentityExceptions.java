package com.wayden.messenger.identity.application;

public final class IdentityExceptions {

  private IdentityExceptions() {}

  public static final class BootstrapAlreadyCompletedException extends RuntimeException {
    public BootstrapAlreadyCompletedException() {
      super("Bootstrap has already been completed");
    }
  }

  public static final class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String message) {
      super(message);
    }
  }

  public static final class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException() {
      super("Invitation was not found");
    }
  }

  public static final class InvitationExpiredException extends RuntimeException {
    public InvitationExpiredException() {
      super("Invitation has expired");
    }
  }

  public static final class InvitationRevokedException extends RuntimeException {
    public InvitationRevokedException() {
      super("Invitation has been revoked");
    }
  }

  public static final class InvitationAlreadyRedeemedException extends RuntimeException {
    public InvitationAlreadyRedeemedException() {
      super("Invitation has already been redeemed");
    }
  }

  public static final class ActorNotAuthorizedException extends RuntimeException {
    public ActorNotAuthorizedException() {
      super("Actor is not authorized for this invitation operation");
    }
  }
}
