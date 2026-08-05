package com.wayden.messenger.bootstrap.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

abstract class FileBackedConfigSource implements ConfigSource {

  private static final Logger LOG = Logger.getLogger(FileBackedConfigSource.class);

  private final Map<String, String> properties;
  private final String name;

  protected FileBackedConfigSource(String sourceName, Path sourcePath) {
    this.name = sourceName + "[" + sourcePath + "]";
    this.properties = loadProperties(sourcePath);
  }

  @Override
  public Map<String, String> getProperties() {
    return Map.copyOf(properties);
  }

  @Override
  public Set<String> getPropertyNames() {
    return properties.keySet();
  }

  @Override
  public String getValue(String propertyName) {
    return properties.get(propertyName);
  }

  @Override
  public String getName() {
    return name;
  }

  protected static Path resolvePath(String overrideEnv, String defaultRelativePath) {
    String override = System.getenv(overrideEnv);
    if (override != null && !override.isBlank()) {
      return Path.of(override).toAbsolutePath().normalize();
    }

    return Path.of(System.getProperty("user.dir"))
        .resolve(defaultRelativePath)
        .toAbsolutePath()
        .normalize();
  }

  private Map<String, String> loadProperties(Path sourcePath) {
    if (!Files.isRegularFile(sourcePath)) {
      return Map.of();
    }

    Properties loadedProperties = new Properties();
    try (InputStream inputStream = Files.newInputStream(sourcePath)) {
      loadedProperties.load(inputStream);
    } catch (IOException exception) {
      LOG.warnf(
          exception,
          "Unable to load config from %s. Falling back to empty config source.",
          sourcePath);
      return Map.of();
    }

    Map<String, String> values = new LinkedHashMap<>();
    for (String propertyName : loadedProperties.stringPropertyNames()) {
      values.put(propertyName, loadedProperties.getProperty(propertyName));
    }

    return Collections.unmodifiableMap(values);
  }
}
