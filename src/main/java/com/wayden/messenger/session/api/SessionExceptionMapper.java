package com.wayden.messenger.session.api;

import com.wayden.messenger.common.api.ApiProblemFactory;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.session.application.SessionExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.USER - 100)
public class SessionExceptionMapper implements ExceptionMapper<SessionExceptions.SessionException> {

  private static final Logger LOG = Logger.getLogger(SessionExceptionMapper.class);

  private final RequestAuditContext auditContext;
  private final ApiProblemFactory problems;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RequestAuditContext is a CDI-managed request-scoped collaborator")
  public SessionExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(SessionExceptions.SessionException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof SessionExceptions.InvalidCredentialsException) {
        return problem(401, "Authentication failed", "INVALID_CREDENTIALS", current.getMessage());
      }
      if (current instanceof SessionExceptions.DisabledUserException) {
        return problem(401, "Authentication failed", "USER_DISABLED", current.getMessage());
      }
      if (current instanceof SessionExceptions.MissingTokenException) {
        return problem(401, "Authentication failed", "MISSING_TOKEN", current.getMessage());
      }
      if (current instanceof SessionExceptions.RevokedSessionException) {
        return problem(401, "Authentication failed", "SESSION_REVOKED", current.getMessage());
      }
      if (current instanceof SessionExceptions.ExpiredSessionException) {
        return problem(401, "Authentication failed", "SESSION_EXPIRED", current.getMessage());
      }
      if (current instanceof SessionExceptions.InvalidSessionException) {
        return problem(401, "Authentication failed", "INVALID_SESSION", current.getMessage());
      }
      if (current instanceof SessionExceptions.SessionUserNotFoundException) {
        return problem(404, "User not found", "USER_NOT_FOUND", current.getMessage());
      }
      if (current instanceof SessionExceptions.RateLimitedException rateLimited) {
        return problems.response(
            429,
            "Authentication rate limited",
            "AUTHENTICATION_RATE_LIMITED",
            rateLimited.getMessage(),
            Math.toIntExact(rateLimited.retryAfterSeconds()));
      }
      current = current.getCause();
    }

    auditContext.recordFailure("SESSION_INTERNAL_ERROR", exception);
    LOG.errorf(
        exception,
        "Session request failed requestId=%s operation=%s code=SESSION_INTERNAL_ERROR",
        auditContext.getRequestId(),
        auditContext.getOperation());
    return problem(500, "Session error", "SESSION_INTERNAL_ERROR", "Unexpected session error");
  }

  private Response problem(int status, String title, String code, String detail) {
    return problems.response(status, title, code, detail);
  }
}
