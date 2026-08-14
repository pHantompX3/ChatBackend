package com.wayden.messenger.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class NetworkSourceResolverTest {

  @Test
  void untrustedPeerCannotSpoofForwardedSourceAddress() {
    var resolution =
        new NetworkSourceResolver("")
            .resolve("198.51.100.7", null, null, requestFrom("203.0.113.10"));

    assertEquals("203.0.113.10", resolution.value());
    assertEquals("vertx-remote-address", resolution.source());
  }

  @Test
  void trustedProxyMaySupplyCanonicalForwardedSourceAddress() {
    var resolution =
        new NetworkSourceResolver("203.0.113.0/24")
            .resolve("198.51.100.7, 203.0.113.10", null, null, requestFrom("203.0.113.10"));

    assertEquals("198.51.100.7", resolution.value());
    assertEquals("x-forwarded-for", resolution.source());
  }

  private static HttpServerRequest requestFrom(String address) {
    return (HttpServerRequest)
        Proxy.newProxyInstance(
            NetworkSourceResolverTest.class.getClassLoader(),
            new Class<?>[] {HttpServerRequest.class},
            (proxy, method, arguments) ->
                "remoteAddress".equals(method.getName())
                    ? SocketAddress.inetSocketAddress(1234, address)
                    : null);
  }
}
