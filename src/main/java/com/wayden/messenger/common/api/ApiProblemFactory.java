package com.wayden.messenger.common.api;

import com.wayden.messenger.common.http.RequestAuditContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Locale;

public final class ApiProblemFactory {

  public static final String MEDIA_TYPE = "application/problem+json";

  private final RequestAuditContext auditContext;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RequestAuditContext is a CDI-managed request-scoped collaborator")
  public ApiProblemFactory(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
  }

  public Response response(int status, String title, String code, String detail) {
    return response(status, title, code, detail, null);
  }

  public Response response(
      int status, String title, String code, String detail, Integer retryAfterSeconds) {
    String requestId = auditContext.getRequestId();
    String safeRequestId = requestId == null || requestId.isBlank() ? "unavailable" : requestId;
    ApiProblem problem =
        new ApiProblem(
            URI.create("urn:wl-chat:problem:" + problemType(code)),
            title,
            status,
            detail == null || detail.isBlank() ? title : detail,
            URI.create("urn:wl-chat:request:" + safeRequestId),
            code,
            safeRequestId);

    Response.ResponseBuilder response = Response.status(status).type(MEDIA_TYPE).entity(problem);
    if (status == Response.Status.UNAUTHORIZED.getStatusCode()) {
      response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    if (retryAfterSeconds != null) {
      response.header("Retry-After", Math.max(1, retryAfterSeconds));
    }
    return response.build();
  }

  private static String problemType(String code) {
    return code.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
