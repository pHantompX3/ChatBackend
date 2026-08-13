package com.wayden.messenger.common.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class JdbcHttpAuditEventSinkIntegrationTest {

  @Inject JdbcHttpAuditEventSink sink;

  @BeforeEach
  void resetAuditTable() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
    }
  }

  @Test
  void persistShouldStoreSchemaVersionAndMappedFields() throws Exception {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T18:00:00Z");
    byte[] recordHash = new byte[] {10, 11, 12, 13};

    sink.persist(
        new HttpAuditEvent(
            eventId,
            "1.0",
            "invitation.created",
            now,
            "req-123",
            "trace-123",
            "identity.invitation.create",
            "POST",
            "/api/v1/invitations",
            "/api/v1/invitations",
            "-",
            200,
            null,
            18,
            now.minusMillis(18),
            now,
            null,
            null,
            "system",
            "invitation",
            "target-1",
            "203.0.113.5",
            "203.0.113.5",
            "x-real-ip",
            "curl/8.8.0",
            "cli",
            "macOS",
            "-",
            "macos",
            "curl",
            Map.of("user-agent", "curl/8.8.0", "x-real-ip", "203.0.113.5"),
            Map.of("content-type", "application/json"),
            null,
            null,
            Map.of("identityEvent", "invitation.created", "metadata.clientIpSource", "x-real-ip"),
            recordHash));

    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement =
            connection.prepareStatement(
                "SELECT schema_version, event_type, request_id, trace_id, operation, method, route_template, "
                    + "path, query, response_status, duration_ms, target_type, target_id, source_ip, remote_ip, "
                    + "ip_resolution_source, user_agent, device_type, device_platform, device_model, os_family, "
                    + "browser_family, request_headers, response_headers, metadata, record_hash "
                    + "FROM [audit].[http_audit_event] WHERE event_id = ?")) {
      statement.setString(1, eventId.toString());
      try (ResultSet resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        assertEquals("1.0", resultSet.getString("schema_version"));
        assertEquals("invitation.created", resultSet.getString("event_type"));
        assertEquals("req-123", resultSet.getString("request_id"));
        assertEquals("trace-123", resultSet.getString("trace_id"));
        assertEquals("identity.invitation.create", resultSet.getString("operation"));
        assertEquals("POST", resultSet.getString("method"));
        assertEquals("/api/v1/invitations", resultSet.getString("route_template"));
        assertEquals("/api/v1/invitations", resultSet.getString("path"));
        assertEquals("-", resultSet.getString("query"));
        assertEquals(200, resultSet.getInt("response_status"));
        assertEquals(18, resultSet.getLong("duration_ms"));
        assertEquals("invitation", resultSet.getString("target_type"));
        assertEquals("target-1", resultSet.getString("target_id"));
        assertEquals("203.0.113.5", resultSet.getString("source_ip"));
        assertEquals("203.0.113.5", resultSet.getString("remote_ip"));
        assertEquals("x-real-ip", resultSet.getString("ip_resolution_source"));
        assertEquals("curl/8.8.0", resultSet.getString("user_agent"));
        assertEquals("cli", resultSet.getString("device_type"));
        assertEquals("macOS", resultSet.getString("device_platform"));
        assertEquals("-", resultSet.getString("device_model"));
        assertEquals("macos", resultSet.getString("os_family"));
        assertEquals("curl", resultSet.getString("browser_family"));

        String requestHeaders = resultSet.getString("request_headers");
        String responseHeaders = resultSet.getString("response_headers");
        String metadata = resultSet.getString("metadata");
        byte[] storedHash = resultSet.getBytes("record_hash");

        assertNotNull(requestHeaders);
        assertNotNull(responseHeaders);
        assertNotNull(metadata);
        assertArrayEquals(recordHash, storedHash);
        assertTrue(requestHeaders.contains("user-agent"));
        assertTrue(requestHeaders.contains("x-real-ip"));
        assertTrue(responseHeaders.contains("content-type"));
        assertTrue(metadata.contains("identityEvent"));
        assertTrue(metadata.contains("metadata.clientIpSource"));
      }
    }
  }

  @Test
  void persistShouldStoreErrorCodeAndResponseCodeForFailureEvents() throws Exception {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-08T18:10:00Z");

    sink.persist(
        new HttpAuditEvent(
            eventId,
            "1.0",
            "invitation.actor.denied",
            now,
            "req-err-1",
            "trace-err-1",
            "identity.invitation.create",
            "POST",
            "/api/v1/invitations",
            "/api/v1/invitations",
            "-",
            403,
            "INVITATION_ACTOR_FORBIDDEN",
            11,
            now.minusMillis(11),
            now,
            null,
            null,
            null,
            "user",
            UUID.randomUUID().toString(),
            "198.51.100.20",
            "198.51.100.20",
            "x-forwarded-for",
            "curl/8.8.0",
            "cli",
            "macOS",
            "-",
            "macos",
            "curl",
            Map.of("x-forwarded-for", "198.51.100.20"),
            Map.of("content-type", "application/problem+json"),
            "INVITATION_ACTOR_FORBIDDEN",
            "Invitation actor is not authorized",
            Map.of(
                "failureCode",
                "INVITATION_ACTOR_FORBIDDEN",
                "failureMessage",
                "Invitation actor is not authorized",
                "failureDetail",
                "Invitation actor is not authorized",
                "failureExceptionType",
                "com.wayden.messenger.identity.application.IdentityExceptions$ActorNotAuthorizedException",
                "failureRootCauseType",
                "java.lang.SecurityException",
                "failureLocation",
                "InvitationServiceImpl.java:42",
                "failureRootCauseLocation",
                "AuthorizationPolicy.java:28"),
            new byte[] {1, 2, 3, 4}));

    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement =
            connection.prepareStatement(
                "SELECT schema_version, response_code, error_code, error_message, response_status, metadata "
                    + "FROM [audit].[http_audit_event] WHERE event_id = ?")) {
      statement.setString(1, eventId.toString());
      try (ResultSet resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        assertEquals("1.0", resultSet.getString("schema_version"));
        assertEquals("INVITATION_ACTOR_FORBIDDEN", resultSet.getString("response_code"));
        assertEquals("INVITATION_ACTOR_FORBIDDEN", resultSet.getString("error_code"));
        assertEquals("Invitation actor is not authorized", resultSet.getString("error_message"));
        assertEquals(403, resultSet.getInt("response_status"));
        assertTrue(resultSet.getString("metadata").contains("INVITATION_ACTOR_FORBIDDEN"));
        assertTrue(resultSet.getString("metadata").contains("java.lang.SecurityException"));
        assertTrue(resultSet.getString("metadata").contains("InvitationServiceImpl.java:42"));
        assertTrue(resultSet.getString("metadata").contains("AuthorizationPolicy.java:28"));
      }
    }
  }
}
