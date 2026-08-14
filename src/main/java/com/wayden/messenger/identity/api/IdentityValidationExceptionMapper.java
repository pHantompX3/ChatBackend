package com.wayden.messenger.identity.api;

import com.wayden.messenger.common.api.ApiProblemFactory;
import com.wayden.messenger.common.http.RequestAuditContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER)
public class IdentityValidationExceptionMapper implements ExceptionMapper<ValidationException> {

  private final ApiProblemFactory problems;

  @Inject
  public IdentityValidationExceptionMapper(RequestAuditContext auditContext) {
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(ValidationException exception) {
    return problem(exception);
  }

  private Response problem(ValidationException exception) {
    String detail =
        exception instanceof ConstraintViolationException violationException
            ? violationException.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Validation failed")
            : exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Validation failed"
                : exception.getMessage();

    return problems.response(400, "Validation failed", "VALIDATION_ERROR", detail);
  }
}
