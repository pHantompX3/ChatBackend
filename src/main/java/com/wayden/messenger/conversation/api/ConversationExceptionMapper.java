package com.wayden.messenger.conversation.api;

import com.wayden.messenger.conversation.application.ConversationExceptions;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
public class ConversationExceptionMapper
    implements ExceptionMapper<ConversationExceptions.ConversationException> {

  @Override
  public Response toResponse(ConversationExceptions.ConversationException exception) {
    if (exception instanceof ConversationExceptions.UserSearchValidationException) {
      return problem(
          400, "Validation failed", "USER_SEARCH_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.ValidationException) {
      return problem(
          400, "Validation failed", "CONVERSATION_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.InvalidCursorException) {
      return problem(400, "Invalid cursor", "INVALID_CURSOR", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.AccessDeniedException) {
      return problem(
          404, "Conversation access denied", "CONVERSATION_ACCESS_DENIED", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.UserNotFoundException) {
      return problem(404, "User not found", "USER_NOT_FOUND", exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.RoleForbiddenException) {
      return problem(
          403,
          "Conversation role forbidden",
          "CONVERSATION_ROLE_FORBIDDEN",
          exception.getMessage());
    }
    if (exception instanceof ConversationExceptions.OwnershipRequiredException) {
      return problem(
          409,
          "Conversation ownership required",
          "CONVERSATION_OWNERSHIP_REQUIRED",
          exception.getMessage());
    }
    return problem(
        500, "Conversation error", "CONVERSATION_INTERNAL_ERROR", "Unexpected conversation error");
  }

  private static Response problem(int status, String title, String code, String detail) {
    return Response.status(status)
        .type("application/problem+json")
        .entity(new ConversationProblem(URI.create("about:blank"), title, status, detail, code))
        .build();
  }

  public record ConversationProblem(
      URI type, String title, int status, String detail, String code) {}
}
