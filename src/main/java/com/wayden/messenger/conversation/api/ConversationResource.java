package com.wayden.messenger.conversation.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.conversation.application.ConversationExceptions;
import com.wayden.messenger.conversation.application.ConversationService;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Path(ApiRoutes.API_V1 + "/conversations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConversationResource {

  private final ConversationService conversationService;

  @POST
  @Path("/direct")
  @AuditOperation("conversation.direct.create")
  public Response createDirect(
      CreateDirectConversationRequest request, @Context ContainerRequestContext context) {
    if (request == null || request.targetUserId() == null) {
      throw validation("targetUserId must not be null");
    }
    var result =
        conversationService.createDirect(
            actor(context), userId(request.targetUserId(), "targetUserId"));
    ConversationResponse response = ConversationResponse.from(result.conversation());
    if (result.created()) {
      return Response.created(location(response.conversationId())).entity(response).build();
    }
    return Response.ok(response).build();
  }

  @POST
  @Path("/groups")
  @AuditOperation("conversation.group.create")
  public Response createGroup(
      CreateGroupConversationRequest request, @Context ContainerRequestContext context) {
    if (request == null) {
      throw validation("Request body must not be empty");
    }
    List<UserId> initialMemberIds =
        request.initialMemberIds() == null
            ? List.of()
            : request.initialMemberIds().stream()
                .map(value -> userId(value, "initialMemberIds"))
                .toList();
    var result = conversationService.createGroup(actor(context), request.title(), initialMemberIds);
    ConversationResponse response = ConversationResponse.from(result);
    return Response.created(location(response.conversationId())).entity(response).build();
  }

  @GET
  @AuditOperation("conversation.list")
  public PageResponse<ConversationResponse> list(
      @QueryParam("cursor") String cursor,
      @QueryParam("limit") Integer limit,
      @Context ContainerRequestContext context) {
    var page = conversationService.list(actor(context), cursor, limit);
    return new PageResponse<>(
        page.items().stream().map(ConversationResponse::from).toList(), page.nextCursor());
  }

  @GET
  @Path("/{conversationId}")
  @AuditOperation("conversation.get")
  public ConversationResponse get(
      @PathParam("conversationId") String conversationId,
      @Context ContainerRequestContext context) {
    return ConversationResponse.from(
        conversationService.get(actor(context), conversationId(conversationId)));
  }

  @GET
  @Path("/{conversationId}/members")
  @AuditOperation("conversation.member.list")
  public PageResponse<ConversationMemberResponse> listMembers(
      @PathParam("conversationId") String conversationId,
      @QueryParam("cursor") String cursor,
      @QueryParam("limit") Integer limit,
      @Context ContainerRequestContext context) {
    var page =
        conversationService.listMembers(
            actor(context), conversationId(conversationId), cursor, limit);
    return new PageResponse<>(
        page.items().stream().map(ConversationMemberResponse::from).toList(), page.nextCursor());
  }

  @PUT
  @Path("/{conversationId}/members/{userId}")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("conversation.member.add")
  public Response addMember(
      @PathParam("conversationId") String conversationId,
      @PathParam("userId") String targetUserId,
      @Context ContainerRequestContext context) {
    conversationService.addMember(
        actor(context), conversationId(conversationId), userId(targetUserId, "userId"));
    return Response.noContent().build();
  }

  @DELETE
  @Path("/{conversationId}/members/{userId}")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("conversation.member.remove")
  public Response removeMember(
      @PathParam("conversationId") String conversationId,
      @PathParam("userId") String targetUserId,
      @Context ContainerRequestContext context) {
    conversationService.removeMember(
        actor(context), conversationId(conversationId), userId(targetUserId, "userId"));
    return Response.noContent().build();
  }

  @POST
  @Path("/{conversationId}/leave")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("conversation.member.leave")
  public Response leave(
      @PathParam("conversationId") String conversationId,
      @Context ContainerRequestContext context) {
    conversationService.leave(actor(context), conversationId(conversationId));
    return Response.noContent().build();
  }

  @PUT
  @Path("/{conversationId}/members/{userId}/role")
  @AuditOperation("conversation.member.role.change")
  public Response changeRole(
      @PathParam("conversationId") String conversationId,
      @PathParam("userId") String targetUserId,
      ChangeConversationRoleRequest request,
      @Context ContainerRequestContext context) {
    if (request == null || request.role() == null) {
      throw validation("role must not be null");
    }
    ConversationRole role;
    try {
      role = ConversationRole.valueOf(request.role().trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw validation("role must be ADMIN or MEMBER");
    }
    conversationService.changeRole(
        actor(context), conversationId(conversationId), userId(targetUserId, "userId"), role);
    return Response.noContent().build();
  }

  @POST
  @Path("/{conversationId}/members/{userId}/transfer-ownership")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("conversation.ownership.transfer")
  public Response transferOwnership(
      @PathParam("conversationId") String conversationId,
      @PathParam("userId") String targetUserId,
      @Context ContainerRequestContext context) {
    conversationService.transferOwnership(
        actor(context), conversationId(conversationId), userId(targetUserId, "userId"));
    return Response.noContent().build();
  }

  private static UserId actor(ContainerRequestContext context) {
    Object value = context.getProperty("authenticatedUserId");
    if (!(value instanceof String rawValue)) {
      throw validation("Authenticated user is required");
    }
    return userId(rawValue, "authenticatedUserId");
  }

  private static ConversationId conversationId(String value) {
    return new ConversationId(uuid(value, "conversationId"));
  }

  private static UserId userId(String value, String field) {
    return new UserId(uuid(value, field));
  }

  private static UUID uuid(String value, String field) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException exception) {
      throw validation("Invalid UUID for field: " + field);
    }
  }

  private static ConversationExceptions.ValidationException validation(String message) {
    return new ConversationExceptions.ValidationException(message);
  }

  private static URI location(UUID conversationId) {
    return URI.create(ApiRoutes.API_V1 + "/conversations/" + conversationId);
  }
}
