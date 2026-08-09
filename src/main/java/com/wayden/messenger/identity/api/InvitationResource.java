package com.wayden.messenger.identity.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.identity.application.CreateInvitationCommand;
import com.wayden.messenger.identity.application.InvitationService;
import com.wayden.messenger.identity.application.RedeemInvitationCommand;
import com.wayden.messenger.identity.application.RevokeInvitationCommand;
import com.wayden.messenger.identity.domain.InvitationId;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Path(ApiRoutes.API_V1 + "/invitations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InvitationResource {

  private final InvitationService invitationService;

  @POST
  @AuditOperation("identity.invitation.create")
  public CreateInvitationResponse createInvitation(@Valid CreateInvitationRequest request) {
    validateCreateInvitationRequest(request);
    var result =
        invitationService.createInvitation(
            new CreateInvitationCommand(parseUserId(request.actorUserId()), request.expiresAt()));

    return new CreateInvitationResponse(result.invitationId().value(), result.rawToken());
  }

  @POST
  @Path("/{invitationId}/revoke")
  @AuditOperation("identity.invitation.revoke")
  public void revokeInvitation(
      @PathParam("invitationId") String invitationId, @Valid RevokeInvitationRequest request) {
    validateRevokeInvitationRequest(request);
    invitationService.revokeInvitation(
        new RevokeInvitationCommand(
            parseInvitationId(invitationId), parseUserId(request.actorUserId())));
  }

  @POST
  @Path("/redeem")
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
