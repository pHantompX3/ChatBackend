package com.wayden.messenger.identity.api;

import com.wayden.messenger.identity.application.IdentityExceptions;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
public class IdentityExceptionMapper implements ExceptionMapper<RuntimeException> {

  @Override
  public Response toResponse(RuntimeException exception) {
    if (exception instanceof BadRequestException badRequestException) {
      return problem(
          400, "Validation failed", "VALIDATION_ERROR", badRequestException.getMessage());
    }
    if (exception instanceof WebApplicationException webApplicationException) {
      return webApplicationException.getResponse();
    }
    if (exception instanceof IdentityExceptions.BootstrapAlreadyCompletedException) {
      return problem(
          409,
          "Bootstrap already completed",
          "BOOTSTRAP_ALREADY_COMPLETED",
          exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.DuplicateUsernameException) {
      return problem(409, "Duplicate username", "DUPLICATE_USERNAME", exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.InvitationNotFoundException) {
      return problem(404, "Invitation not found", "INVITATION_NOT_FOUND", exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.InvitationExpiredException) {
      return problem(410, "Invitation expired", "INVITATION_EXPIRED", exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.InvitationRevokedException) {
      return problem(422, "Invitation revoked", "INVITATION_REVOKED", exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.InvitationAlreadyRedeemedException) {
      return problem(
          422,
          "Invitation already redeemed",
          "INVITATION_ALREADY_REDEEMED",
          exception.getMessage());
    }
    if (exception instanceof IdentityExceptions.ActorNotAuthorizedException) {
      return problem(
          403, "Invitation actor forbidden", "INVITATION_ACTOR_FORBIDDEN", exception.getMessage());
    }
    return problem(500, "Identity error", "IDENTITY_INTERNAL_ERROR", "Unexpected identity error");
  }

  private static Response problem(int status, String title, String code, String detail) {
    IdentityProblem payload =
        new IdentityProblem(
            URI.create("about:blank"), title, status, detail == null ? title : detail, code);
    return Response.status(status).type("application/problem+json").entity(payload).build();
  }

  public record IdentityProblem(URI type, String title, int status, String detail, String code) {}
}
