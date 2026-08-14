package com.wayden.messenger.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiSnapshotTest {

  @Test
  void committedSnapshotMustDescribeTheCurrentSecurityAndThrottleContract() throws IOException {
    Path snapshot = Path.of("docs/api/openapi.json");
    assertTrue(Files.isRegularFile(snapshot));
    JsonNode document = new ObjectMapper().readTree(snapshot.toFile());

    assertEquals("3.1.0", document.path("openapi").asText());
    assertNotNull(document.path("components").path("securitySchemes").get("bearerAuth"));
    assertTrue(document.path("paths").size() >= 20);
    assertNotNull(
        document.path("paths").path("/api/v1/sessions").path("post").path("responses").get("429"));
    assertEquals(
        0, document.path("paths").path("/api/v1/sessions").path("post").path("security").size());
  }
}
