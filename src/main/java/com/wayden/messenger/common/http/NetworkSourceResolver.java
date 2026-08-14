package com.wayden.messenger.common.http;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class NetworkSourceResolver {

  private final List<String> trustedProxies;

  @Inject
  public NetworkSourceResolver(
      @ConfigProperty(name = "chat.http.trusted-proxies") Optional<String> trustedProxies) {
    this(trustedProxies.orElse(""));
  }

  NetworkSourceResolver(String trustedProxies) {
    this.trustedProxies =
        Arrays.stream(trustedProxies.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
  }

  Resolution resolve(
      String forwardedFor, String xRealIp, String forwarded, HttpServerRequest serverRequest) {
    String peer =
        serverRequest == null || serverRequest.remoteAddress() == null
            ? null
            : canonical(serverRequest.remoteAddress().hostAddress());
    if (peer == null) {
      return new Resolution("-", "missing");
    }
    if (!isTrusted(peer)) {
      return new Resolution(peer, "vertx-remote-address");
    }

    String candidate = firstForwardedFor(forwardedFor);
    String source = "x-forwarded-for";
    if (candidate == null) {
      candidate = canonical(xRealIp);
      source = "x-real-ip";
    }
    if (candidate == null) {
      candidate = forwardedValue(forwarded);
      source = "forwarded";
    }
    return candidate == null
        ? new Resolution(peer, "trusted-proxy-fallback")
        : new Resolution(candidate, source);
  }

  private boolean isTrusted(String peer) {
    return trustedProxies.stream().anyMatch(entry -> contains(entry, peer));
  }

  private static String firstForwardedFor(String value) {
    if (value == null || value.isBlank() || "-".equals(value)) {
      return null;
    }
    return canonical(value.split(",", 2)[0].trim());
  }

  private static String forwardedValue(String value) {
    if (value == null || value.isBlank() || "-".equals(value)) {
      return null;
    }
    for (String element : value.split(",")) {
      for (String part : element.split(";")) {
        String trimmed = part.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
          String candidate = canonical(trimmed.substring(4).replace("\"", "").trim());
          if (candidate != null) {
            return candidate;
          }
        }
      }
    }
    return null;
  }

  private static boolean contains(String configuredRange, String address) {
    try {
      String[] parts = configuredRange.split("/", 2);
      byte[] network = numericAddress(parts[0]).getAddress();
      byte[] candidate = numericAddress(address).getAddress();
      if (network.length != candidate.length) {
        return false;
      }
      int bits = parts.length == 1 ? network.length * 8 : Integer.parseInt(parts[1]);
      if (bits < 0 || bits > network.length * 8) {
        return false;
      }
      int wholeBytes = bits / 8;
      int remainingBits = bits % 8;
      for (int index = 0; index < wholeBytes; index++) {
        if (network[index] != candidate[index]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (8 - remainingBits);
      return (network[wholeBytes] & mask) == (candidate[wholeBytes] & mask);
    } catch (IllegalArgumentException | UnknownHostException exception) {
      return false;
    }
  }

  private static String canonical(String rawAddress) {
    if (rawAddress == null || rawAddress.isBlank() || "-".equals(rawAddress)) {
      return null;
    }
    String value = rawAddress.trim();
    if (value.startsWith("[") && value.contains("]")) {
      value = value.substring(1, value.indexOf(']'));
    }
    try {
      return numericAddress(value).getHostAddress();
    } catch (UnknownHostException exception) {
      return null;
    }
  }

  private static InetAddress numericAddress(String value) throws UnknownHostException {
    if (!value.matches("[0-9A-Fa-f:.]+")) {
      throw new UnknownHostException("Network source is not a numeric IP address");
    }
    return InetAddress.getByName(value);
  }

  record Resolution(String value, String source) {}
}
