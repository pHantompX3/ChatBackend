package com.wayden.messenger.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JdbcHttpAuditEventSink implements HttpAuditEventSink {

  private static final String INSERT_SQL =
      "INSERT INTO [audit].[http_audit_event] "
          + "(event_id, schema_version, event_type, occurred_at, request_id, trace_id, operation, "
          + "method, route_template, path, query, response_status, response_code, duration_ms, "
          + "request_timestamp, response_timestamp, actor_user_id, actor_username, actor_auth_type, "
          + "target_type, target_id, source_ip, remote_ip, ip_resolution_source, user_agent, "
          + "device_type, device_platform, device_model, os_family, browser_family, request_headers, "
          + "response_headers, error_code, error_message, metadata, record_hash) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  @Override
  public void persist(HttpAuditEvent event) {
    try (var connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
      bind(statement, event);
      statement.executeUpdate();
    } catch (SQLException sqlException) {
      throw new IllegalStateException("Failed to persist HTTP audit event", sqlException);
    }
  }

  private void bind(PreparedStatement statement, HttpAuditEvent event) throws SQLException {
    statement.setString(1, event.eventId().toString());
    statement.setString(2, event.schemaVersion());
    statement.setString(3, event.eventType());
    statement.setObject(4, toUtcDateTime(event.occurredAt()));
    statement.setString(5, event.requestId());
    setNullableString(statement, 6, event.traceId());
    statement.setString(7, event.operation());
    statement.setString(8, event.method());
    setNullableString(statement, 9, event.routeTemplate());
    statement.setString(10, event.path());
    setNullableString(statement, 11, event.query());
    statement.setInt(12, event.responseStatus());
    setNullableString(statement, 13, event.responseCode());
    statement.setLong(14, event.durationMs());
    statement.setObject(15, toUtcDateTime(event.requestTimestamp()));
    statement.setObject(16, toUtcDateTime(event.responseTimestamp()));
    setNullableUuid(statement, 17, event.actorUserId());
    setNullableString(statement, 18, event.actorUsername());
    setNullableString(statement, 19, event.actorAuthType());
    setNullableString(statement, 20, event.targetType());
    setNullableString(statement, 21, event.targetId());
    setNullableString(statement, 22, event.sourceIp());
    setNullableString(statement, 23, event.remoteIp());
    setNullableString(statement, 24, event.ipResolutionSource());
    setNullableString(statement, 25, event.userAgent());
    setNullableString(statement, 26, event.deviceType());
    setNullableString(statement, 27, event.devicePlatform());
    setNullableString(statement, 28, event.deviceModel());
    setNullableString(statement, 29, event.osFamily());
    setNullableString(statement, 30, event.browserFamily());
    setNullableString(statement, 31, toJson(event.requestHeaders()));
    setNullableString(statement, 32, toJson(event.responseHeaders()));
    setNullableString(statement, 33, event.errorCode());
    setNullableString(statement, 34, event.errorMessage());
    setNullableString(statement, 35, toJson(event.metadata()));
    statement.setBytes(36, event.recordHash());
  }

  private static LocalDateTime toUtcDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private void setNullableString(PreparedStatement statement, int index, String value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.NVARCHAR);
      return;
    }
    statement.setString(index, value);
  }

  private void setNullableUuid(PreparedStatement statement, int index, UUID value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.CHAR);
      return;
    }
    statement.setString(index, value.toString());
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException jsonProcessingException) {
      throw new IllegalStateException(
          "Failed to serialize audit JSON payload", jsonProcessingException);
    }
  }
}
