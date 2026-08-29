package com.wayden.messenger.common.configuration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DatabaseTlsPolicy {

  private final boolean verifiedTlsRequired;
  private final String jdbcUrl;

  @Inject
  public DatabaseTlsPolicy(
      @ConfigProperty(name = "chat.database.require-verified-tls", defaultValue = "false")
          boolean verifiedTlsRequired,
      @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl) {
    this.verifiedTlsRequired = verifiedTlsRequired;
    this.jdbcUrl = jdbcUrl;
  }

  void validateAtStartup(@Observes StartupEvent ignored) {
    validate(verifiedTlsRequired, jdbcUrl);
  }

  static void validate(boolean verifiedTlsRequired, String jdbcUrl) {
    if (!verifiedTlsRequired) {
      return;
    }
    Map<String, String> properties = sqlServerProperties(jdbcUrl);
    if (!"true".equals(properties.get("encrypt"))
        || !"false".equals(properties.get("trustservercertificate"))) {
      throw new IllegalStateException(
          "Hardened database connections require encrypt=true and trustServerCertificate=false");
    }
  }

  private static Map<String, String> sqlServerProperties(String jdbcUrl) {
    Map<String, String> properties = new HashMap<>();
    if (jdbcUrl == null || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver://")) {
      return properties;
    }
    for (String segment : jdbcUrl.split(";")) {
      int separator = segment.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      String key = segment.substring(0, separator).trim().toLowerCase(Locale.ROOT);
      String value = segment.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
      properties.put(key, value);
    }
    return properties;
  }
}
