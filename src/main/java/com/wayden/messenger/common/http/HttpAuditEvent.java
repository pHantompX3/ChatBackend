package com.wayden.messenger.common.http;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record HttpAuditEvent(
    UUID eventId,
    String schemaVersion,
    String eventType,
    Instant occurredAt,
    String requestId,
    String traceId,
    String operation,
    String method,
    String routeTemplate,
    String path,
    String query,
    int responseStatus,
    String responseCode,
    long durationMs,
    Instant requestTimestamp,
    Instant responseTimestamp,
    UUID actorUserId,
    String actorUsername,
    String actorAuthType,
    String targetType,
    String targetId,
    String sourceIp,
    String remoteIp,
    String ipResolutionSource,
    String userAgent,
    String deviceType,
    String devicePlatform,
    String deviceModel,
    String osFamily,
    String browserFamily,
    Map<String, Object> requestHeaders,
    Map<String, Object> responseHeaders,
    String errorCode,
    String errorMessage,
    Map<String, String> metadata,
    byte[] recordHash) {

  public HttpAuditEvent {
    requestHeaders = requestHeaders == null ? Map.of() : Map.copyOf(requestHeaders);
    responseHeaders = responseHeaders == null ? Map.of() : Map.copyOf(responseHeaders);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    recordHash = recordHash == null ? new byte[0] : recordHash.clone();
  }

  @Override
  public Map<String, Object> requestHeaders() {
    return Map.copyOf(requestHeaders);
  }

  @Override
  public Map<String, Object> responseHeaders() {
    return Map.copyOf(responseHeaders);
  }

  @Override
  public Map<String, String> metadata() {
    return Map.copyOf(metadata);
  }

  @Override
  public byte[] recordHash() {
    return recordHash.clone();
  }
}
