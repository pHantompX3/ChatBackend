package com.wayden.messenger.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Provider
@Priority(Priorities.AUTHENTICATION)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);

  public static final String REQUEST_ID_PROPERTY = "wlChat.requestId";
  public static final String TRACE_ID_PROPERTY = "wlChat.traceId";
  public static final String REQUEST_START_NANO_PROPERTY = "wlChat.requestStartNanos";
  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  private final RequestAuditContext requestAuditContext;
  private final ObjectMapper objectMapper;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String requestId = UUID.randomUUID().toString();
    String traceId =
        Optional.ofNullable(requestContext.getHeaderString(TRACE_ID_HEADER))
            .filter(value -> !value.isBlank())
            .orElse(requestId);

    requestContext.setProperty(REQUEST_ID_PROPERTY, requestId);
    requestContext.setProperty(TRACE_ID_PROPERTY, traceId);
    requestContext.setProperty(REQUEST_START_NANO_PROPERTY, System.nanoTime());
    requestAuditContext.setRequestId(requestId);
    requestAuditContext.setTraceId(traceId);

    MDC.put("requestId", requestId);
    MDC.put("traceId", traceId);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", "incoming.request");
    payload.put("requestId", requestId);
    payload.put("traceId", traceId);
    payload.put("method", requestContext.getMethod());
    payload.put("path", requestContext.getUriInfo().getRequestUri().getPath());
    payload.put(
        "query",
        Optional.ofNullable(requestContext.getUriInfo().getRequestUri().getQuery()).orElse("-"));
    payload.put(
        "userAgent", Optional.ofNullable(requestContext.getHeaderString("User-Agent")).orElse("-"));

    LOG.infof("=== ENTRY FILTER: New Request Received ===%n%s", toPrettyJson(payload));
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
  }

  private String toPrettyJson(Map<String, Object> payload) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (JsonProcessingException ignored) {
      return payload.toString();
    }
  }
}
