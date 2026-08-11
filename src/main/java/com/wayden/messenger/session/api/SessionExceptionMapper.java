package com.wayden.messenger.session.api;

import com.wayden.messenger.session.application.SessionExceptions;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
@Priority(Priorities.USER - 100)
public class SessionExceptionMapper implements ExceptionMapper<SessionExceptions.SessionException> {

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
      current = current.getCause();
    }

    return null;
  }

  private static Response problem(int status, String title, String code, String detail) {
    SessionProblem payload =
        new SessionProblem(
            URI.create("about:blank"), title, status, detail == null ? title : detail, code);
    return Response.status(status).type("application/problem+json").entity(payload).build();
  }

  public record SessionProblem(URI type, String title, int status, String detail, String code) {}
}
