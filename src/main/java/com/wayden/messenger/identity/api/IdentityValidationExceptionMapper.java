package com.wayden.messenger.identity.api;

import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
@Priority(Priorities.USER)
public class IdentityValidationExceptionMapper implements ExceptionMapper<ValidationException> {

  @Override
  public Response toResponse(ValidationException exception) {
    return problem(exception);
  }

  private static Response problem(ValidationException exception) {
    String detail =
        exception instanceof ConstraintViolationException violationException
            ? violationException.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Validation failed")
            : exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Validation failed"
                : exception.getMessage();

    IdentityExceptionMapper.IdentityProblem payload =
        new IdentityExceptionMapper.IdentityProblem(
            URI.create("about:blank"), "Validation failed", 400, detail, "VALIDATION_ERROR");
    return Response.status(400).type("application/problem+json").entity(payload).build();
  }
}
