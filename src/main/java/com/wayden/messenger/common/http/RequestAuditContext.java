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
}
