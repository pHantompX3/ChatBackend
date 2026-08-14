package com.wayden.messenger.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.common.api.ApiProblem;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quarkus.runtime.configuration.MemorySize;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.math.BigInteger;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpFailureAdapter {

  private static final Logger LOG = Logger.getLogger(HttpFailureAdapter.class);
  private static final int SIZE_PREFLIGHT_ORDER = -3;
  private static final int FAILURE_HANDLER_ORDER = 9_999;

  private final ObjectMapper objectMapper;
  private final long maxBodySize;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is an application-scoped CDI-managed collaborator")
  public HttpFailureAdapter(
      ObjectMapper objectMapper,
      @ConfigProperty(name = "quarkus.http.limits.max-body-size")
          Optional<MemorySize> configuredMaxBodySize) {
    this.objectMapper = objectMapper;
    this.maxBodySize =
        configuredMaxBodySize
            .orElseGet(() -> new MemorySize(BigInteger.valueOf(10L * 1024 * 1024)))
            .asLongValue();
  }

  void register(@Observes Router router) {
    router.route("/api/v1/*").order(SIZE_PREFLIGHT_ORDER).handler(this::rejectKnownOversizeBody);
    router
        .route("/api/v1/*")
        .order(FAILURE_HANDLER_ORDER)
        .failureHandler(this::handleApplicationFailure);
  }

  private void rejectKnownOversizeBody(RoutingContext context) {
    String value = context.request().getHeader("Content-Length");
    try {
      if (value != null && Long.parseLong(value) > maxBodySize) {
        writePayloadTooLarge(context);
        return;
      }
    } catch (NumberFormatException ignored) {
      // The HTTP layer owns malformed Content-Length handling.
    }
    context.next();
  }

  private void handleApplicationFailure(RoutingContext context) {
    if (context.statusCode() != 413 || context.response().ended()) {
      context.next();
      return;
    }

    writePayloadTooLarge(context);
  }

  private void writePayloadTooLarge(RoutingContext context) {
    String requestId = UUID.randomUUID().toString();
    String traceId =
        RequestIdFilter.validTraceId(context.request().getHeader(RequestIdFilter.TRACE_ID_HEADER))
            .orElse(requestId);
    ApiProblem problem =
        new ApiProblem(
            URI.create("urn:wl-chat:problem:payload-too-large"),
            "Payload too large",
            413,
            "Request payload exceeds the configured limit",
            URI.create("urn:wl-chat:request:" + requestId),
            "PAYLOAD_TOO_LARGE",
            requestId);

    try {
      context
          .response()
          .setStatusCode(413)
          .putHeader("Content-Type", "application/problem+json")
          .putHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId)
          .putHeader(RequestIdFilter.TRACE_ID_HEADER, traceId)
          .end(objectMapper.writeValueAsString(problem));
    } catch (JsonProcessingException exception) {
      LOG.error("Failed to serialize the transport failure response", exception);
      context.response().setStatusCode(413).end();
    }
  }
}
