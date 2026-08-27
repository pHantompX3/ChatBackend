package com.wayden.messenger.realtime.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class WebSocketHandshakePolicy {

  private final boolean queryTokenEnabled;
  private final boolean originAllowlistRequired;
  private final Set<String> allowedOrigins;

  @Inject
  public WebSocketHandshakePolicy(
      @ConfigProperty(name = "chat.websocket.query-token-enabled", defaultValue = "true")
          boolean queryTokenEnabled,
      @ConfigProperty(name = "chat.websocket.origin-allowlist-required", defaultValue = "false")
          boolean originAllowlistRequired,
      @ConfigProperty(name = "chat.websocket.allowed-origins") Optional<String> allowedOrigins) {
    this.queryTokenEnabled = queryTokenEnabled;
    this.originAllowlistRequired = originAllowlistRequired;
    this.allowedOrigins = parseOrigins(allowedOrigins.orElse(""));
    if (originAllowlistRequired && this.allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException(
          "chat.websocket.allowed-origins is required when the Origin allowlist is enforced");
    }
  }

  WebSocketHandshakePolicy(
      boolean queryTokenEnabled, boolean originAllowlistRequired, String allowedOrigins) {
    this(queryTokenEnabled, originAllowlistRequired, Optional.ofNullable(allowedOrigins));
  }

  boolean queryTokenEnabled() {
    return queryTokenEnabled;
  }

  boolean allowsOrigin(String origin) {
    if (origin == null || origin.isBlank()) {
      return true;
    }
    if (allowedOrigins.isEmpty()) {
      return !originAllowlistRequired;
    }
    return allowedOrigins.contains(normalizeOrigin(origin));
  }

  private static Set<String> parseOrigins(String configuredOrigins) {
    if (configuredOrigins == null || configuredOrigins.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(configuredOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .map(WebSocketHandshakePolicy::normalizeOrigin)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String normalizeOrigin(String origin) {
    String normalized = origin.trim().toLowerCase(Locale.ROOT);
    return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }
}
