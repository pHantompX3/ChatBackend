package com.wayden.messenger.common.http;

import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@RequestScoped
@Getter
@Setter
public class RequestAuditContext {

  private static final int ERROR_MESSAGE_LIMIT = 256;
  private static final int ERROR_DETAIL_LIMIT = 2048;

  private String requestId;
  private String traceId;
  private String operation;
  private String method;
  private String path;
  private String query;
  private String userAgent;
  private String forwardedFor;
  private String xRealIp;
  private String clientIp;
  private String deviceType;
  private String deviceModel;
  private String devicePlatform;
  private String deviceMobileHint;
  private String osFamily;
  private String browserFamily;
  private Integer responseStatus;
  private Integer responseLength;
  private Long durationMs;
  private final Map<String, String> customAttributes = new HashMap<>();

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getForwardedFor() {
    return forwardedFor;
  }

  public void setForwardedFor(String forwardedFor) {
    this.forwardedFor = forwardedFor;
  }

  public String getXRealIp() {
    return xRealIp;
  }

  public void setXRealIp(String xRealIp) {
    this.xRealIp = xRealIp;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(String deviceType) {
    this.deviceType = deviceType;
  }

  public String getDeviceModel() {
    return deviceModel;
  }

  public void setDeviceModel(String deviceModel) {
    this.deviceModel = deviceModel;
  }

  public String getDevicePlatform() {
    return devicePlatform;
  }

  public void setDevicePlatform(String devicePlatform) {
    this.devicePlatform = devicePlatform;
  }

  public String getDeviceMobileHint() {
    return deviceMobileHint;
  }

  public void setDeviceMobileHint(String deviceMobileHint) {
    this.deviceMobileHint = deviceMobileHint;
  }

  public String getOsFamily() {
    return osFamily;
  }

  public void setOsFamily(String osFamily) {
    this.osFamily = osFamily;
  }

  public String getBrowserFamily() {
    return browserFamily;
  }

  public void setBrowserFamily(String browserFamily) {
    this.browserFamily = browserFamily;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public void setResponseStatus(Integer responseStatus) {
    this.responseStatus = responseStatus;
  }

  public Integer getResponseLength() {
    return responseLength;
  }

  public void setResponseLength(Integer responseLength) {
    this.responseLength = responseLength;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public Map<String, String> getCustomAttributes() {
    return Collections.unmodifiableMap(customAttributes);
  }

  public void putCustomAttribute(String key, String value) {
    if (key != null && value != null) {
      customAttributes.put(key, value);
      if ("operation".equals(key)) {
        this.operation = value;
      }
    }
  }

  public void recordFailure(String code, Throwable failure) {
    if (code == null || code.isBlank() || failure == null) {
      return;
    }

    Throwable rootCause = rootCause(failure);
    String rootMessage =
        rootCause.getMessage() == null || rootCause.getMessage().isBlank()
            ? rootCause.getClass().getName()
            : rootCause.getMessage();

    putCustomAttribute("failureCode", code);
    putCustomAttribute("failureMessage", truncate(rootMessage, ERROR_MESSAGE_LIMIT));
    putCustomAttribute("failureDetail", truncate(rootMessage, ERROR_DETAIL_LIMIT));
    putCustomAttribute("failureExceptionType", failure.getClass().getName());
    putCustomAttribute("failureRootCauseType", rootCause.getClass().getName());
    putCustomAttribute("failureLocation", location(failure));
    putCustomAttribute("failureRootCauseLocation", location(rootCause));
  }

  public void redactQuery() {
    this.query = "REDACTED";
  }

  public void redactNetworkIdentity() {
    this.forwardedFor = "REDACTED";
    this.xRealIp = "REDACTED";
    this.clientIp = "REDACTED";
  }

  public void redactDeviceIdentity() {
    this.userAgent = "REDACTED";
    this.deviceType = "REDACTED";
    this.deviceModel = "REDACTED";
    this.devicePlatform = "REDACTED";
    this.deviceMobileHint = "REDACTED";
    this.osFamily = "REDACTED";
    this.browserFamily = "REDACTED";
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private static String location(Throwable failure) {
    StackTraceElement[] stackTrace = failure.getStackTrace();
    if (stackTrace.length == 0) {
      return "unknown";
    }
    for (StackTraceElement element : stackTrace) {
      if (element.getClassName().startsWith("com.wayden.messenger.")) {
        return element.toString();
      }
    }
    return stackTrace[0].toString();
  }

  private static String truncate(String value, int limit) {
    return value.length() <= limit ? value : value.substring(0, limit);
  }
}
