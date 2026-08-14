package com.wayden.messenger.common.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wayden.messenger.common.http.RequestAuditContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.USER + 500)
public class ApiRuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {

  private static final Logger LOG = Logger.getLogger(ApiRuntimeExceptionMapper.class);

  private final RequestAuditContext auditContext;
  private final ApiProblemFactory problems;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RequestAuditContext is a CDI-managed request-scoped collaborator")
  public ApiRuntimeExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(RuntimeException exception) {
    Mapping mapping = mapping(exception);
    if (mapping.status() >= 500) {
      auditContext.recordFailure(mapping.code(), exception);
      LOG.errorf(
          exception,
          "Unexpected API failure requestId=%s operation=%s code=%s",
          auditContext.getRequestId(),
          auditContext.getOperation(),
          mapping.code());
    }
    return problems.response(mapping.status(), mapping.title(), mapping.code(), mapping.detail());
  }

  private static Mapping mapping(RuntimeException exception) {
    if (hasCause(exception, JsonProcessingException.class)) {
      return new Mapping(400, "Malformed JSON", "MALFORMED_JSON", "Request body is not valid JSON");
    }
    if (exception instanceof BadRequestException) {
      return new Mapping(400, "Validation failed", "VALIDATION_ERROR", safeDetail(exception));
    }
    if (exception instanceof NotFoundException) {
      return new Mapping(404, "Resource not found", "RESOURCE_NOT_FOUND", "Resource was not found");
    }
    if (exception instanceof NotAllowedException) {
      return new Mapping(405, "Method not allowed", "METHOD_NOT_ALLOWED", "Method is not allowed");
    }
    if (exception instanceof NotSupportedException) {
      return new Mapping(
          415, "Unsupported media type", "UNSUPPORTED_MEDIA_TYPE", "Media type is not supported");
    }
    if (exception instanceof NotAcceptableException) {
      return new Mapping(
          406, "Not acceptable", "NOT_ACCEPTABLE", "Requested representation is not available");
    }
    if (exception instanceof ClientErrorException clientError) {
      return statusMapping(clientError.getResponse().getStatus());
    }
    if (exception instanceof WebApplicationException webApplicationException) {
      return statusMapping(webApplicationException.getResponse().getStatus());
    }
    return new Mapping(
        500, "Internal server error", "INTERNAL_ERROR", "An unexpected error occurred");
  }

  private static Mapping statusMapping(int status) {
    return switch (status) {
      case 400 ->
          new Mapping(400, "Validation failed", "VALIDATION_ERROR", "Request validation failed");
      case 401 ->
          new Mapping(401, "Authentication failed", "INVALID_SESSION", "Authentication failed");
      case 403 -> new Mapping(403, "Forbidden", "FORBIDDEN", "Request is not permitted");
      case 404 ->
          new Mapping(404, "Resource not found", "RESOURCE_NOT_FOUND", "Resource was not found");
      case 405 ->
          new Mapping(405, "Method not allowed", "METHOD_NOT_ALLOWED", "Method is not allowed");
      case 406 ->
          new Mapping(
              406, "Not acceptable", "NOT_ACCEPTABLE", "Requested representation is not available");
      case 413 ->
          new Mapping(
              413,
              "Payload too large",
              "PAYLOAD_TOO_LARGE",
              "Request payload exceeds the configured limit");
      case 415 ->
          new Mapping(
              415,
              "Unsupported media type",
              "UNSUPPORTED_MEDIA_TYPE",
              "Media type is not supported");
      default ->
          new Mapping(status, "Request failed", "REQUEST_FAILED", "Request could not be processed");
    };
  }

  private static String safeDetail(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? "Request validation failed" : message;
  }

  private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
    Throwable current = exception;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record Mapping(int status, String title, String code, String detail) {}
}
