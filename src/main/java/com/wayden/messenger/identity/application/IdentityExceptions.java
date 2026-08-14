package com.wayden.messenger.identity.application;

public final class IdentityExceptions {

  private IdentityExceptions() {}

  public abstract static class IdentityException extends RuntimeException {
    protected IdentityException(String message) {
      super(message);
    }
  }

  public static final class BootstrapAlreadyCompletedException extends IdentityException {
    public BootstrapAlreadyCompletedException() {
      super("Bootstrap has already been completed");
    }
  }

  public static final class DuplicateUsernameException extends IdentityException {
    public DuplicateUsernameException(String message) {
      super(message);
    }
  }

  public static final class InvitationNotFoundException extends IdentityException {
    public InvitationNotFoundException() {
      super("Invitation was not found");
    }
  }

  public static final class InvitationExpiredException extends IdentityException {
    public InvitationExpiredException() {
      super("Invitation has expired");
    }
  }

  public static final class InvitationRevokedException extends IdentityException {
    public InvitationRevokedException() {
      super("Invitation has been revoked");
    }
  }

  public static final class InvitationAlreadyRedeemedException extends IdentityException {
    public InvitationAlreadyRedeemedException() {
      super("Invitation has already been redeemed");
    }
  }

  public static final class ActorNotAuthorizedException extends IdentityException {
    public ActorNotAuthorizedException() {
      super("Actor is not authorized for this invitation operation");
    }
  }
}
