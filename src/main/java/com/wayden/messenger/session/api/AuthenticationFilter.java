package com.wayden.messenger.session.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.identity.domain.SystemRole;
import com.wayden.messenger.session.application.SessionExceptions;
import com.wayden.messenger.session.application.SessionService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@Provider
@Priority(Priorities.AUTHENTICATION)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthenticationFilter implements ContainerRequestFilter {

  private static final Set<String> PUBLIC_PATHS =
      Set.of(
          ApiRoutes.API_V1 + "/bootstrap/admin",
          ApiRoutes.API_V1 + "/invitations/redeem",
          ApiRoutes.API_V1 + "/ping");

  private final SessionService sessionService;
  private final RequestAuditContext requestAuditContext;

  @Context private ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = normalizePath(requestContext.getUriInfo().getPath());
    boolean publicEndpoint = isPublicEndpoint(requestContext, path);

    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      if (publicEndpoint) {
        return;
      }
      abort(requestContext, Response.Status.UNAUTHORIZED, "MISSING_TOKEN");
      return;
    }

    if (!hasBearerScheme(authorizationHeader)) {
      abort(requestContext, Response.Status.UNAUTHORIZED, "INVALID_SESSION");
      return;
    }

    String rawToken = extractBearerToken(authorizationHeader);
    try {
      var user = sessionService.resolveAuthenticatedUser(rawToken);
      requestContext.setProperty("authenticatedUserId", user.id().value().toString());
      requestContext.setProperty("authenticatedUsername", user.username());
      requestContext.setProperty("authenticatedUserRole", user.systemRole().name());
      enforceRole(requestContext, user.systemRole());
      requestAuditContext.putCustomAttribute("actorUserId", user.id().value().toString());
      requestAuditContext.putCustomAttribute("actorUsername", user.username());
      requestAuditContext.putCustomAttribute("actorAuthType", "session");
    } catch (SessionExceptions.SessionException exception) {
      abort(requestContext, Response.Status.UNAUTHORIZED, errorCode(exception));
    }
  }

  private boolean isPublicEndpoint(ContainerRequestContext requestContext, String path) {
    if (PUBLIC_PATHS.contains(path)) {
      return true;
    }

    Method resourceMethod = resourceInfo.getResourceMethod();
    if (resourceMethod != null && resourceMethod.isAnnotationPresent(PublicEndpoint.class)) {
      return true;
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    if (resourceClass != null && resourceClass.isAnnotationPresent(PublicEndpoint.class)) {
      return true;
    }

    return false;
  }

  private void abort(ContainerRequestContext requestContext, Response.Status status, String code) {
    requestContext.abortWith(
        Response.status(status)
            .type("application/problem+json")
            .entity(
                new SessionExceptionMapper.SessionProblem(
                    java.net.URI.create("about:blank"),
                    "Authentication failed",
                    status.getStatusCode(),
                    code,
                    code))
            .build());
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    if (!path.startsWith("/")) {
      return "/" + path;
    }
    return path;
  }

  private void enforceRole(ContainerRequestContext requestContext, SystemRole userRole) {
    Method resourceMethod = resourceInfo.getResourceMethod();
    if (resourceMethod != null) {
      RequiresRole requiredRole = resourceMethod.getAnnotation(RequiresRole.class);
      if (requiredRole != null && !Arrays.asList(requiredRole.value()).contains(userRole)) {
        abort(requestContext, Response.Status.FORBIDDEN, "FORBIDDEN");
        return;
      }
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    if (resourceClass != null) {
      RequiresRole requiredRole = resourceClass.getAnnotation(RequiresRole.class);
      if (requiredRole != null && !Arrays.asList(requiredRole.value()).contains(userRole)) {
        abort(requestContext, Response.Status.FORBIDDEN, "FORBIDDEN");
      }
    }
  }

  private String errorCode(RuntimeException exception) {
    if (exception
        instanceof
        com.wayden.messenger.session.application.SessionExceptions.RevokedSessionException) {
      return "SESSION_REVOKED";
    }
    if (exception
        instanceof
        com.wayden.messenger.session.application.SessionExceptions.ExpiredSessionException) {
      return "SESSION_EXPIRED";
    }
    if (exception
        instanceof
        com.wayden.messenger.session.application.SessionExceptions.InvalidSessionException) {
      return "INVALID_SESSION";
    }
    if (exception
        instanceof
        com.wayden.messenger.session.application.SessionExceptions.DisabledUserException) {
      return "USER_DISABLED";
    }
    if (exception
        instanceof
        com.wayden.messenger.session.application.SessionExceptions.MissingTokenException) {
      return "MISSING_TOKEN";
    }
    return "INVALID_CREDENTIALS";
  }

  private static boolean hasBearerScheme(String authorizationHeader) {
    return authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7);
  }

  private static String extractBearerToken(String authorizationHeader) {
    return authorizationHeader.substring(7).trim();
  }
}
