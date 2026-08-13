package com.wayden.messenger.conversation.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.conversation.application.ConversationExceptions;
import com.wayden.messenger.conversation.application.UserDirectoryService;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Path(ApiRoutes.API_V1 + "/users")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UserDirectoryResource {

  private final UserDirectoryService userDirectoryService;

  @GET
  @AuditOperation("identity.user.search")
  public PageResponse<UserSummaryResponse> search(
      @QueryParam("query") String query,
      @QueryParam("cursor") String cursor,
      @QueryParam("limit") Integer limit,
      @Context ContainerRequestContext context) {
    var page = userDirectoryService.search(actor(context), query, cursor, limit);
    return new PageResponse<>(
        page.items().stream().map(UserSummaryResponse::from).toList(), page.nextCursor());
  }

  private static UserId actor(ContainerRequestContext context) {
    Object value = context.getProperty("authenticatedUserId");
    if (!(value instanceof String rawValue)) {
      throw new ConversationExceptions.ValidationException("Authenticated user is required");
    }
    try {
      return new UserId(UUID.fromString(rawValue));
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.ValidationException("Authenticated user is invalid");
    }
  }
}
