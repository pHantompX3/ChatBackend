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
    invitationService.revokeInvitation(
        new RevokeInvitationCommand(
            parseInvitationId(invitationId), parseUserId(request.actorUserId())));
  }

  @POST
  @Path("/redeem")
  @AuditOperation("identity.invitation.redeem")
  public RedeemInvitationResponse redeemInvitation(@Valid RedeemInvitationRequest request) {
    var result =
        invitationService.redeemInvitation(
            new RedeemInvitationCommand(
                request.invitationToken(), request.username(), request.password()));

    return new RedeemInvitationResponse(result.userId().value(), result.username());
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
