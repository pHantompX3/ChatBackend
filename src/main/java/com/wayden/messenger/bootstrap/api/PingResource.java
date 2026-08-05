package com.wayden.messenger.bootstrap.api;

import com.wayden.messenger.bootstrap.service.PingService;
import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@Path(ApiRoutes.API_V1 + "/ping")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PingResource {

  private final PingService pingService;

  @GET
  @AuditOperation("health.ping")
  public PingResponse ping() {
    return pingService.ping();
  }
}
