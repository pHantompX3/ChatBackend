package com.wayden.messenger.common.http;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);
  private static final int MAX_TRACE_ID_LENGTH = 128;
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");

  public static final String REQUEST_ID_PROPERTY = "wlChat.requestId";
  public static final String TRACE_ID_PROPERTY = "wlChat.traceId";
  public static final String REQUEST_START_NANO_PROPERTY = "wlChat.requestStartNanos";
  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  private final RequestAuditContext requestAuditContext;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String requestId = UUID.randomUUID().toString();
    String traceId =
        validTraceId(requestContext.getHeaderString(TRACE_ID_HEADER)).orElse(requestId);

    requestContext.setProperty(REQUEST_ID_PROPERTY, requestId);
    requestContext.setProperty(TRACE_ID_PROPERTY, traceId);
    requestContext.setProperty(REQUEST_START_NANO_PROPERTY, System.nanoTime());
    requestAuditContext.setRequestId(requestId);
    requestAuditContext.setTraceId(traceId);

    MDC.put("requestId", requestId);
    MDC.put("traceId", traceId);
    MDC.put("httpMethod", requestContext.getMethod());
    MDC.put("httpPath", requestContext.getUriInfo().getRequestUri().getPath());
    LOG.info("incoming.request");
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    Object requestId = requestContext.getProperty(REQUEST_ID_PROPERTY);
    Object traceId = requestContext.getProperty(TRACE_ID_PROPERTY);
    if (requestId != null) {
      responseContext.getHeaders().putSingle(REQUEST_ID_HEADER, requestId.toString());
    }
    if (traceId != null) {
      responseContext.getHeaders().putSingle(TRACE_ID_HEADER, traceId.toString());
    }

    MDC.remove("requestId");
    MDC.remove("traceId");
    MDC.remove("httpMethod");
    MDC.remove("httpPath");
  }

  static Optional<String> validTraceId(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_TRACE_ID_LENGTH) {
      return Optional.empty();
    }
    return TRACE_ID_PATTERN.matcher(value).matches() ? Optional.of(value) : Optional.empty();
  }
}
