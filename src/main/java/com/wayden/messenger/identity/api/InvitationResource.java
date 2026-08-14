package com.wayden.messenger.identity.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.identity.application.CreateInvitationCommand;
import com.wayden.messenger.identity.application.InvitationService;
import com.wayden.messenger.identity.application.RedeemInvitationCommand;
import com.wayden.messenger.identity.application.RevokeInvitationCommand;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.api.PublicEndpoint;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;

@Path(ApiRoutes.API_V1 + "/invitations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InvitationResource {

  private final InvitationService invitationService;

  @POST
  @AuditOperation("identity.invitation.create")
  public CreateInvitationResponse createInvitation(
      @Valid CreateInvitationRequest request, @Context ContainerRequestContext requestContext) {
    validateCreateInvitationRequest(request);
    var result =
        invitationService.createInvitation(
            new CreateInvitationCommand(
                resolveActorUserId(request, requestContext), request.expiresAt()));

    return new CreateInvitationResponse(result.invitationId().value(), result.rawToken());
  }

  @POST
  @Path("/{invitationId}/revoke")
  @APIResponse(responseCode = "204", description = "Invitation revoked")
  @AuditOperation("identity.invitation.revoke")
  public Response revokeInvitation(
      @PathParam("invitationId") String invitationId,
      @Valid RevokeInvitationRequest request,
      @Context ContainerRequestContext requestContext) {
    validateRevokeInvitationRequest(request);
    invitationService.revokeInvitation(
        new RevokeInvitationCommand(
            parseInvitationId(invitationId), resolveActorUserId(request, requestContext)));
    return Response.noContent().build();
  }

  @POST
  @Path("/redeem")
  @PublicEndpoint
  @SecurityRequirements
  @AuditOperation("identity.invitation.redeem")
  public RedeemInvitationResponse redeemInvitation(@Valid RedeemInvitationRequest request) {
    validateRedeemInvitationRequest(request);
    var result =
        invitationService.redeemInvitation(
            new RedeemInvitationCommand(
                request.invitationToken(), request.username(), request.password()));

    return new RedeemInvitationResponse(result.userId().value(), result.username());
  }

  private static void validateCreateInvitationRequest(CreateInvitationRequest request) {
    if (request == null) {
      throw new BadRequestException("Request body must not be empty");
    }
    if (request.actorUserId() == null || request.actorUserId().isBlank()) {
      throw new BadRequestException("actorUserId must not be blank");
    }
    if (request.expiresAt() == null) {
      throw new BadRequestException("expiresAt must not be null");
    }
    if (!request.expiresAt().isAfter(Instant.now())) {
      throw new BadRequestException("expiresAt must be in the future");
    }
  }

  private static void validateRevokeInvitationRequest(RevokeInvitationRequest request) {
    if (request == null) {
      throw new BadRequestException("Request body must not be empty");
    }
    if (request.actorUserId() == null || request.actorUserId().isBlank()) {
      throw new BadRequestException("actorUserId must not be blank");
    }
  }

  private static void validateRedeemInvitationRequest(RedeemInvitationRequest request) {
    if (request == null) {
      throw new BadRequestException("Request body must not be empty");
    }
    if (request.invitationToken() == null || request.invitationToken().isBlank()) {
      throw new BadRequestException("invitationToken must not be blank");
    }
    if (request.username() == null || request.username().isBlank()) {
      throw new BadRequestException("username must not be blank");
    }
    if (request.password() == null || request.password().isBlank()) {
      throw new BadRequestException("password must not be blank");
    }
  }

  private static UserId parseUserId(String raw) {
    return new UserId(parseUuid(raw, "actorUserId"));
  }

  private static UserId resolveActorUserId(
      CreateInvitationRequest request, ContainerRequestContext requestContext) {
    String authenticatedUserId =
        requestContext == null ? null : (String) requestContext.getProperty("authenticatedUserId");
    if (authenticatedUserId != null && !authenticatedUserId.isBlank()) {
      return new UserId(parseUuid(authenticatedUserId, "authenticatedUserId"));
    }
    return parseUserId(request.actorUserId());
  }

  private static UserId resolveActorUserId(
      RevokeInvitationRequest request, ContainerRequestContext requestContext) {
    String authenticatedUserId =
        requestContext == null ? null : (String) requestContext.getProperty("authenticatedUserId");
    if (authenticatedUserId != null && !authenticatedUserId.isBlank()) {
      return new UserId(parseUuid(authenticatedUserId, "authenticatedUserId"));
    }
    return parseUserId(request.actorUserId());
  }

  private static InvitationId parseInvitationId(String raw) {
    return new InvitationId(parseUuid(raw, "invitationId"));
  }

  private static UUID parseUuid(String raw, String field) {
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid UUID for field: " + field);
    }
  }
}
