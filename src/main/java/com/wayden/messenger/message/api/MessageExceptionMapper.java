package com.wayden.messenger.message.api;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.message.application.MessageExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import org.jboss.logging.Logger;

@Provider
public class MessageExceptionMapper implements ExceptionMapper<MessageExceptions.MessageException> {

  private static final Logger LOG = Logger.getLogger(MessageExceptionMapper.class);
  private final RequestAuditContext auditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "RequestAuditContext is CDI-managed request-scoped state shared during request handling.")
  public MessageExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
  }

  @Override
  public Response toResponse(MessageExceptions.MessageException exception) {
    ProblemMapping mapping = mapping(exception);
    auditContext.putCustomAttribute("eventType", "message.request.failed");
    auditContext.recordFailure(mapping.code(), exception);
    if (exception instanceof MessageExceptions.InternalException) {
      LOG.errorf(
          exception,
          "Message request failed requestId=%s operation=%s code=%s",
          auditContext.getRequestId(),
          auditContext.getOperation(),
          mapping.code());
    }
    return Response.status(mapping.status())
        .type("application/problem+json")
        .entity(
            new MessageProblem(
                URI.create("about:blank"),
                mapping.title(),
                mapping.status(),
                mapping.detail(),
                mapping.code()))
        .build();
  }

  private static ProblemMapping mapping(MessageExceptions.MessageException exception) {
    if (exception instanceof MessageExceptions.ValidationException) {
      return new ProblemMapping(
          400, "Message validation failed", "MESSAGE_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof MessageExceptions.AccessDeniedException) {
      return new ProblemMapping(
          404, "Message access denied", "MESSAGE_ACCESS_DENIED", exception.getMessage());
    }
    if (exception instanceof MessageExceptions.EditForbiddenException) {
      return new ProblemMapping(
          403, "Message edit forbidden", "MESSAGE_EDIT_FORBIDDEN", exception.getMessage());
    }
    if (exception instanceof MessageExceptions.DeleteForbiddenException) {
      return new ProblemMapping(
          403, "Message deletion forbidden", "MESSAGE_DELETE_FORBIDDEN", exception.getMessage());
    }
    if (exception instanceof MessageExceptions.IdempotencyConflictException) {
      return new ProblemMapping(
          409,
          "Message idempotency conflict",
          "MESSAGE_IDEMPOTENCY_CONFLICT",
          exception.getMessage());
    }
    return new ProblemMapping(
        500, "Message error", "MESSAGE_INTERNAL_ERROR", "Unexpected message error");
  }

  public record MessageProblem(URI type, String title, int status, String detail, String code) {}

  private record ProblemMapping(int status, String title, String code, String detail) {}
}
