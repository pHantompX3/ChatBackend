package com.wayden.messenger.session.api;

import com.wayden.messenger.common.api.ApiProblem;
import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.session.application.SessionService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;

@Path(ApiRoutes.API_V1 + "/sessions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionResource {

  private final SessionService sessionService;
  private final RequestAuditContext requestAuditContext;

  @POST
  @PublicEndpoint
  @SecurityRequirements
  @APIResponses({
    @APIResponse(responseCode = "200", description = "Authenticated session created"),
    @APIResponse(
        responseCode = "400",
        description = "Malformed or invalid request",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblem.class))),
    @APIResponse(
        responseCode = "401",
        description = "Invalid credentials",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblem.class))),
    @APIResponse(
        responseCode = "429",
        description = "Authentication attempt limit exhausted",
        headers =
            @Header(
                name = "Retry-After",
                description = "Seconds until another attempt is permitted"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblem.class))),
    @APIResponse(
        responseCode = "500",
        description = "Authentication service unavailable",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ApiProblem.class)))
  })
  @AuditOperation("identity.session.create")
  public SessionLoginResponse login(@Valid SessionLoginRequest request) {
    validateLoginRequest(request);
    var result =
        sessionService.login(
            new SessionService.LoginCommand(
                request.username(),
                request.password(),
                requestAuditContext.getUserAgent(),
                requestAuditContext.getClientIp()));
    return new SessionLoginResponse(result.sessionId(), result.token());
  }

  @POST
  @Path("/logout")
  @APIResponse(responseCode = "204", description = "Session revoked")
  @AuditOperation("identity.session.revoke")
  public Response logout(@HeaderParam("Authorization") String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      throw new BadRequestException("Authorization header must not be blank");
    }
    String rawToken = stripBearerPrefix(authorizationHeader);
    sessionService.logout(new SessionService.LogoutCommand(rawToken));
    return Response.noContent().build();
  }

  @POST
  @Path("/users/{userId}/revoke-all")
  @Consumes(MediaType.WILDCARD)
  @RequiresRole(SystemRole.ADMIN)
  @APIResponse(responseCode = "204", description = "All target-user sessions revoked")
  @AuditOperation("identity.session.revoke-all")
  public Response revokeAllSessions(@PathParam("userId") String userId) {
    sessionService.revokeAllSessionsForUser(
        new SessionService.RevokeAllSessionsCommand(parseUserId(userId)));
    return Response.noContent().build();
  }

  private void validateLoginRequest(SessionLoginRequest request) {
    if (request == null) {
      throw new BadRequestException("Request body must not be empty");
    }
  }

  private static String stripBearerPrefix(String authorizationHeader) {
    if (authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorizationHeader.substring(7).trim();
    }
    return authorizationHeader;
  }

  private static UserId parseUserId(String rawUserId) {
    try {
      return new UserId(UUID.fromString(rawUserId));
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("Invalid UUID for field: userId");
    }
  }
}
