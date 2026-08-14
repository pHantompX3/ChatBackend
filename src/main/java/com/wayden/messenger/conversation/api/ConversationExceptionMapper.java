package com.wayden.messenger.conversation.api;

import com.wayden.messenger.common.api.ApiProblemFactory;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.application.ConversationExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class ConversationExceptionMapper
    implements ExceptionMapper<ConversationExceptions.ConversationException> {

  private static final Logger LOG = Logger.getLogger(ConversationExceptionMapper.class);

  private final RequestAuditContext auditContext;
  private final ApiProblemFactory problems;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "RequestAuditContext is CDI-managed request-scoped state intentionally shared within request handling.")
  public ConversationExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(ConversationExceptions.ConversationException exception) {
    ProblemMapping mapping = mapping(exception);
    auditContext.putCustomAttribute("identityEvent", "conversation.request.failed");
    auditContext.recordFailure(mapping.code(), exception);

    if (exception instanceof ConversationExceptions.InternalException) {
      LOG.errorf(
          exception,
          "Conversation request failed requestId=%s operation=%s code=%s",
          auditContext.getRequestId(),
          auditContext.getOperation(),
          mapping.code());
    }

    return problem(mapping.status(), mapping.title(), mapping.code(), mapping.detail());
  }

  private static ProblemMapping mapping(ConversationExceptions.ConversationException exception) {
    if (exception instanceof ConversationExceptions.UserSearchValidationException) {
      return new ProblemMapping(
          400, "Validation failed", "USER_SEARCH_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.ValidationException) {
      return new ProblemMapping(
          400, "Validation failed", "CONVERSATION_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.InvalidCursorException) {
      return new ProblemMapping(400, "Invalid cursor", "INVALID_CURSOR", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.AccessDeniedException) {
      return new ProblemMapping(
          404, "Conversation access denied", "CONVERSATION_ACCESS_DENIED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.UserNotFoundException) {
      return new ProblemMapping(404, "User not found", "USER_NOT_FOUND", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.RoleForbiddenException) {
      return new ProblemMapping(
          403,
          "Conversation role forbidden",
          "CONVERSATION_ROLE_FORBIDDEN",
          exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.OwnershipRequiredException) {
      return new ProblemMapping(
          409,
          "Conversation ownership required",
          "CONVERSATION_OWNERSHIP_REQUIRED",
          exception.getMessage());
    }
    return new ProblemMapping(
        500, "Conversation error", "CONVERSATION_INTERNAL_ERROR", "Unexpected conversation error");
  }

  private Response problem(int status, String title, String code, String detail) {
    return problems.response(status, title, code, detail);
  }

  private record ProblemMapping(int status, String title, String code, String detail) {}
}
