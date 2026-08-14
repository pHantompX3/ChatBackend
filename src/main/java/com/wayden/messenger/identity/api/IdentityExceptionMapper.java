package com.wayden.messenger.identity.api;

import com.wayden.messenger.common.api.ApiProblemFactory;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.application.IdentityExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class IdentityExceptionMapper
    implements ExceptionMapper<IdentityExceptions.IdentityException> {

  private static final Logger LOG = Logger.getLogger(IdentityExceptionMapper.class);

  private final RequestAuditContext auditContext;
  private final ApiProblemFactory problems;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RequestAuditContext is a CDI-managed request-scoped collaborator")
  public IdentityExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(IdentityExceptions.IdentityException exception) {
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
    auditContext.recordFailure("IDENTITY_INTERNAL_ERROR", exception);
    LOG.errorf(
        exception,
        "Identity request failed requestId=%s operation=%s code=IDENTITY_INTERNAL_ERROR",
        auditContext.getRequestId(),
        auditContext.getOperation());
    return problem(500, "Identity error", "IDENTITY_INTERNAL_ERROR", "Unexpected identity error");
  }

  private Response problem(int status, String title, String code, String detail) {
    return problems.response(status, title, code, detail);
  }
}
