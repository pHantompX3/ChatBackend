package com.wayden.messenger.delivery.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryExceptions;
import com.wayden.messenger.delivery.application.DeliveryService;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.domain.MessageId;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

@Path(ApiRoutes.API_V1 + "/conversations/{conversationId}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DeliveryResource {

  private final DeliveryService deliveryService;

  @PUT
  @Path("/delivery-position")
  @APIResponse(responseCode = "204", description = "Delivery position acknowledged")
  @AuditOperation("delivery.position.acknowledge")
  public Response acknowledgeDelivery(
      @PathParam("conversationId") String rawConversationId,
      AcknowledgePositionRequest request,
      @Context ContainerRequestContext context) {
    requireBody(request);
    deliveryService.acknowledgeDelivery(
        actor(context), conversationId(rawConversationId), sequence(request));
    return Response.noContent().build();
  }

  @PUT
  @Path("/read-position")
  @APIResponse(responseCode = "204", description = "Read position acknowledged")
  @AuditOperation("read.position.acknowledge")
  public Response acknowledgeRead(
      @PathParam("conversationId") String rawConversationId,
      AcknowledgePositionRequest request,
      @Context ContainerRequestContext context) {
    requireBody(request);
    deliveryService.acknowledgeRead(
        actor(context), conversationId(rawConversationId), sequence(request));
    return Response.noContent().build();
  }

  @GET
  @Path("/position")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("delivery.position.get")
  public MessagePositionResponse getPosition(
      @PathParam("conversationId") String rawConversationId,
      @Context ContainerRequestContext context) {
    ConversationId conversationId = conversationId(rawConversationId);
    return MessagePositionResponse.from(
        conversationId, deliveryService.getPosition(actor(context), conversationId));
  }

  @GET
  @Path("/messages/{messageId}/status")
  @Consumes(MediaType.WILDCARD)
  @AuditOperation("message.delivery-status.get")
  public MessageDeliveryStatusResponse getStatus(
      @PathParam("conversationId") String rawConversationId,
      @PathParam("messageId") String rawMessageId,
      @Context ContainerRequestContext context) {
    return MessageDeliveryStatusResponse.from(
        deliveryService.getStatus(
            actor(context), conversationId(rawConversationId), messageId(rawMessageId)));
  }

  private static void requireBody(AcknowledgePositionRequest request) {
    if (request == null) {
      throw validation("Request body must not be empty");
    }
  }

  private static Long sequence(AcknowledgePositionRequest request) {
    var sequence = request.sequence();
    if (sequence == null || sequence.isNull()) {
      return null;
    }
    if (!sequence.isIntegralNumber() || !sequence.canConvertToLong()) {
      throw validation("sequence must be an integer within the signed 64-bit range");
    }
    return sequence.longValue();
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

  private static DeliveryExceptions.ValidationException validation(String message) {
    return new DeliveryExceptions.ValidationException(message);
  }
}
