package com.wayden.messenger.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.USER)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HttpAuditFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final Logger LOG = Logger.getLogger(HttpAuditFilter.class);

  private final RequestAuditContext requestAuditContext;
  private final ObjectMapper objectMapper;

  @Context ResourceInfo resourceInfo;

  @Context HttpServerRequest httpServerRequest;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String requestId = getRequestId(requestContext);
    String method = requestContext.getMethod();
    String path = requestContext.getUriInfo().getRequestUri().getPath();
    String query = requestContext.getUriInfo().getRequestUri().getQuery();
    String userAgent =
        Optional.ofNullable(requestContext.getHeaderString("User-Agent")).orElse("-");
    String forwardedFor =
        Optional.ofNullable(requestContext.getHeaderString("X-Forwarded-For")).orElse("-");
    String xRealIp = Optional.ofNullable(requestContext.getHeaderString("X-Real-IP")).orElse("-");
    String forwarded = Optional.ofNullable(requestContext.getHeaderString("Forwarded")).orElse("-");
    String platformHint = normalizeHint(requestContext.getHeaderString("Sec-CH-UA-Platform"));
    String mobileHint = normalizeHint(requestContext.getHeaderString("Sec-CH-UA-Mobile"));
    String modelHint = normalizeHint(requestContext.getHeaderString("Sec-CH-UA-Model"));
    ClientIpResolution clientIpResolution =
        resolveClientIp(forwardedFor, xRealIp, forwarded, httpServerRequest);
    DeviceDetection deviceDetection = detectDeviceType(userAgent, mobileHint, modelHint);

    requestAuditContext.setRequestId(requestId);
    requestAuditContext.setMethod(method);
    requestAuditContext.setPath(path);
    requestAuditContext.setQuery(query == null ? "-" : query);
    requestAuditContext.setUserAgent(userAgent);
    requestAuditContext.setForwardedFor(forwardedFor);
    requestAuditContext.setXRealIp(xRealIp);
    requestAuditContext.setClientIp(clientIpResolution.value());
    requestAuditContext.setDeviceType(deviceDetection.type());
    requestAuditContext.setDeviceModel(modelHint);
    requestAuditContext.setDevicePlatform(platformHint);
    requestAuditContext.setDeviceMobileHint(mobileHint);
    requestAuditContext.setOsFamily(detectOsFamily(userAgent, platformHint));
    requestAuditContext.setBrowserFamily(detectBrowserFamily(userAgent));
    requestAuditContext.setOperation(resolveOperationName());
    requestAuditContext.putCustomAttribute("metadata.clientIpSource", clientIpResolution.source());
    requestAuditContext.putCustomAttribute("metadata.deviceTypeSource", deviceDetection.source());

    LOG.debugf(
        "AUDIT FILTER: Step 1/3 - Captured request metadata (requestId=%s)",
        requestAuditContext.getRequestId());
    LOG.debugf(
        "AUDIT FILTER: Step 2/3 - Classified operation and client context (operation=%s)",
        requestAuditContext.getOperation());
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    int status = responseContext.getStatus();
    int length = responseContext.getLength();

    long durationMs = -1L;
    Object start = requestContext.getProperty(RequestIdFilter.REQUEST_START_NANO_PROPERTY);
    if (start instanceof Long startNanos) {
      durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
    }

    requestAuditContext.setResponseStatus(status);
    requestAuditContext.setResponseLength(length);
    requestAuditContext.setDurationMs(durationMs);

    LOG.debugf(
        "AUDIT FILTER: Step 3/3 - Emitting audit snapshot (requestId=%s)",
        requestAuditContext.getRequestId());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", "audit.completed");
    payload.put("requestId", requestAuditContext.getRequestId());
    payload.put("traceId", requestAuditContext.getTraceId());
    payload.put("operation", requestAuditContext.getOperation());
    payload.put("method", requestAuditContext.getMethod());
    payload.put("path", requestAuditContext.getPath());
    payload.put("query", requestAuditContext.getQuery());
    payload.put(
        "network",
        Map.of(
            "clientIp", requestAuditContext.getClientIp(),
            "forwardedFor", requestAuditContext.getForwardedFor(),
            "xRealIp", requestAuditContext.getXRealIp()));
    payload.put(
        "device",
        Map.of(
            "userAgent", requestAuditContext.getUserAgent(),
            "deviceType", requestAuditContext.getDeviceType(),
            "deviceModel", requestAuditContext.getDeviceModel(),
            "platform", requestAuditContext.getDevicePlatform(),
            "mobileHint", requestAuditContext.getDeviceMobileHint(),
            "os", requestAuditContext.getOsFamily(),
            "browser", requestAuditContext.getBrowserFamily()));
    payload.put(
        "response",
        Map.of(
            "status", requestAuditContext.getResponseStatus(),
            "durationMs", requestAuditContext.getDurationMs(),
            "responseLength", requestAuditContext.getResponseLength()));
    payload.put("metadata", requestAuditContext.getCustomAttributes());

    LOG.debugf("=== AUDIT FILTER: Request Audit Snapshot ===%n%s", toPrettyJson(payload));
  }

  private String getRequestId(ContainerRequestContext requestContext) {
    Object requestId = requestContext.getProperty(RequestIdFilter.REQUEST_ID_PROPERTY);
    return requestId == null ? "-" : requestId.toString();
  }

  private String resolveOperationName() {
    Method method = resourceInfo == null ? null : resourceInfo.getResourceMethod();
    if (method == null) {
      return "unknown.operation";
    }

    AuditOperation annotation = method.getAnnotation(AuditOperation.class);
    if (annotation != null && annotation.value() != null && !annotation.value().isBlank()) {
      return annotation.value();
    }

    return method.getName();
  }

  private ClientIpResolution resolveClientIp(
      String forwardedFor, String xRealIp, String forwarded, HttpServerRequest serverRequest) {
    if (forwardedFor != null && !forwardedFor.isBlank() && !"-".equals(forwardedFor)) {
      return new ClientIpResolution(forwardedFor.split(",")[0].trim(), "x-forwarded-for");
    }
    if (xRealIp != null && !xRealIp.isBlank() && !"-".equals(xRealIp)) {
      return new ClientIpResolution(xRealIp.trim(), "x-real-ip");
    }
    if (forwarded != null && !forwarded.isBlank() && !"-".equals(forwarded)) {
      String[] parts = forwarded.split(";");
      for (String part : parts) {
        String trimmed = part.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
          return new ClientIpResolution(trimmed.substring(4).replace("\"", "").trim(), "forwarded");
        }
      }
    }
    if (serverRequest != null && serverRequest.remoteAddress() != null) {
      String remoteAddress = serverRequest.remoteAddress().hostAddress();
      if (remoteAddress != null && !remoteAddress.isBlank()) {
        return new ClientIpResolution(remoteAddress, "vertx-remote-address");
      }
    }
    return new ClientIpResolution("-", "missing");
  }

  private DeviceDetection detectDeviceType(String userAgent, String mobileHint, String modelHint) {
    if ("?1".equals(mobileHint)) {
      return new DeviceDetection("mobile", "sec-ch-ua-mobile");
    }
    if ("?0".equals(mobileHint)) {
      return new DeviceDetection("desktop", "sec-ch-ua-mobile");
    }
    if (!"-".equals(modelHint)) {
      return new DeviceDetection("mobile", "sec-ch-ua-model");
    }

    String ua = userAgent == null ? "" : userAgent.toLowerCase();
    if (ua.contains("postmanruntime")) {
      return new DeviceDetection("api-client", "user-agent");
    }
    if (ua.contains("curl/")) {
      return new DeviceDetection("cli", "user-agent");
    }
    if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawl")) {
      return new DeviceDetection("bot", "user-agent");
    }
    if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
      return new DeviceDetection("mobile", "user-agent");
    }
    if (ua.isBlank() || "-".equals(userAgent)) {
      return new DeviceDetection("unknown", "missing");
    }
    return new DeviceDetection("desktop", "user-agent");
  }

  private String normalizeHint(String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    return value.replace("\"", "").trim();
  }

  private String detectOsFamily(String userAgent, String platformHint) {
    String platform = platformHint == null ? "" : platformHint.toLowerCase().replace("\"", "");
    if (platform.contains("windows")) {
      return "windows";
    }
    if (platform.contains("mac")) {
      return "macos";
    }
    if (platform.contains("android")) {
      return "android";
    }
    if (platform.contains("linux")) {
      return "linux";
    }

    String ua = userAgent == null ? "" : userAgent.toLowerCase();
    if (ua.contains("windows")) {
      return "windows";
    }
    if (ua.contains("darwin")) {
      return "macos";
    }
    if (ua.contains("mac os") || ua.contains("macintosh")) {
      return "macos";
    }
    if (ua.contains("android")) {
      return "android";
    }
    if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
      return "ios";
    }
    if (ua.contains("linux")) {
      return "linux";
    }
    return "unknown";
  }

  private String detectBrowserFamily(String userAgent) {
    String ua = userAgent == null ? "" : userAgent.toLowerCase();
    if (ua.contains("postmanruntime")) {
      return "postman";
    }
    if (ua.contains("edg/")) {
      return "edge";
    }
    if (ua.contains("chrome/") && !ua.contains("edg/")) {
      return "chrome";
    }
    if (ua.contains("safari/") && !ua.contains("chrome/")) {
      return "safari";
    }
    if (ua.contains("firefox/")) {
      return "firefox";
    }
    if (ua.contains("curl/")) {
      return "curl";
    }
    return "unknown";
  }

  private String toPrettyJson(Map<String, Object> payload) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (JsonProcessingException ignored) {
      return payload.toString();
    }
  }

  private record ClientIpResolution(String value, String source) {}

  private record DeviceDetection(String type, String source) {}
}
