package com.wayden.messenger.identity.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.identity.application.AdminService;
import com.wayden.messenger.identity.application.BootstrapAdminCommand;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path(ApiRoutes.API_V1 + "/bootstrap")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AdminResource {

  private final AdminService adminService;

  @POST
  @Path("/admin")
  @AuditOperation("identity.bootstrap.admin")
  public BootstrapAdminResponse bootstrapAdmin(@Valid BootstrapAdminRequest request) {
    var result =
        adminService.bootstrapFirstAdmin(
            new BootstrapAdminCommand(request.username(), request.password()));
    return new BootstrapAdminResponse(result.userId().value(), result.username());
  }
}
