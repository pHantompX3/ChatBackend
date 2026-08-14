package com.wayden.messenger.message.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageExceptions;
import com.wayden.messenger.message.application.MessageService;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.MessageId;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path(ApiRoutes.API_V1 + "/conversations/{conversationId}/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConversationMessageResource {

  private final MessageService messageService;

  @POST
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Existing idempotent message submission",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = MessageResponse.class))),
    @APIResponse(
        responseCode = "201",
        description = "Message durably accepted",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = MessageResponse.class)))
  })
  @AuditOperation("message.send")
  public Response send(
      @PathParam("conversationId") String rawConversationId,
      SendMessageRequest request,
      @Context ContainerRequestContext context) {
    if (request == null) {
      throw validation("Request body must not be empty");
    }
    ConversationId conversationId = conversationId(rawConversationId);
    var result =
        messageService.send(
            actor(context),
            conversationId,
            new ClientMessageId(uuid(request.clientMessageId(), "clientMessageId")),
            request.body());
    MessageResponse response = MessageResponse.from(result.message());
    if (result.created()) {
      return Response.created(location(conversationId, result.message().id()))
          .entity(response)
          .build();
    }
    return Response.ok(response).build();
  }

  @GET
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("message.history.list")
  public MessagePageResponse list(
      @PathParam("conversationId") String rawConversationId,
      @QueryParam("afterSequence") String rawAfterSequence,
      @QueryParam("limit") String rawLimit,
      @Context ContainerRequestContext context) {
    var page =
        messageService.list(
            actor(context),
            conversationId(rawConversationId),
            optionalLong(rawAfterSequence, "afterSequence"),
            optionalInteger(rawLimit, "limit"));
    return new MessagePageResponse(
        page.items().stream().map(MessageResponse::from).toList(), page.nextAfterSequence());
  }

  @PUT
  @Path("/{messageId}")
  @AuditOperation("message.edit")
  public MessageResponse edit(
      @PathParam("conversationId") String rawConversationId,
      @PathParam("messageId") String rawMessageId,
      EditMessageRequest request,
      @Context ContainerRequestContext context) {
    if (request == null) {
      throw validation("Request body must not be empty");
    }
    return MessageResponse.from(
        messageService.edit(
            actor(context),
            conversationId(rawConversationId),
            messageId(rawMessageId),
            request.body()));
  }

  @DELETE
  @Path("/{messageId}")
  @Consumes(MediaType.WILDCARD)
  @APIResponse(responseCode = "204", description = "Message soft-deleted")
  @AuditOperation("message.delete")
  public Response delete(
      @PathParam("conversationId") String rawConversationId,
      @PathParam("messageId") String rawMessageId,
      @Context ContainerRequestContext context) {
    messageService.delete(
        actor(context), conversationId(rawConversationId), messageId(rawMessageId));
    return Response.noContent().build();
  }

  private static UserId actor(ContainerRequestContext context) {
    Object value = context.getProperty("authenticatedUserId");
    if (!(value instanceof String rawValue)) {
      throw validation("Authenticated user is required");
    }
    return new UserId(uuid(rawValue, "authenticatedUserId"));
  }

  private static ConversationId conversationId(String value) {
    return new ConversationId(uuid(value, "conversationId"));
  }

  private static MessageId messageId(String value) {
    return new MessageId(uuid(value, "messageId"));
  }

  private static UUID uuid(String value, String field) {
    if (value == null || value.isBlank()) {
      throw validation(field + " must not be null or blank");
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw validation("Invalid UUID for field: " + field);
    }
  }

  private static Long optionalLong(String value, String field) {
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      throw validation(field + " must be an integer");
    }
  }

  private static Integer optionalInteger(String value, String field) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      throw validation(field + " must be an integer");
    }
  }

  private static MessageExceptions.ValidationException validation(String message) {
    return new MessageExceptions.ValidationException(message);
  }

  private static URI location(ConversationId conversationId, MessageId messageId) {
    return URI.create(
        ApiRoutes.API_V1
            + "/conversations/"
            + conversationId.value()
            + "/messages/"
            + messageId.value());
  }
}
