import sql from 'mssql';
import { parseTelemetryEnvelope } from '../../shared/telemetry-envelope.js';

export async function readAuditProjectionPage(pool, after, limit, sourceInstanceId) {
  const request = pool.request();
  request.input('createdAt', sql.VarChar(33), withoutUtcSuffix(after.createdAt));
  request.input('eventId', sql.VarChar(36), after.eventId);
  request.input('pageSize', sql.Int, limit);
  const result = await request.query(`
    SET LOCK_TIMEOUT 2000;
    SET DEADLOCK_PRIORITY LOW;
    SELECT TOP (@pageSize)
      event_id,
      CONVERT(varchar(33), created_at, 126) AS created_at_cursor,
      occurred_at, operation, method, route_template,
      response_status, response_code, duration_ms, error_code
    FROM audit.monitoring_http_event
    WHERE created_at > CONVERT(datetime2(7), @createdAt, 126)
       OR (
         created_at = CONVERT(datetime2(7), @createdAt, 126)
         AND LOWER(CONVERT(char(36), event_id)) COLLATE Latin1_General_100_BIN2
           > @eventId COLLATE Latin1_General_100_BIN2
       )
    ORDER BY created_at,
      LOWER(CONVERT(char(36), event_id)) COLLATE Latin1_General_100_BIN2;
  `);

  return result.recordset.map((row) => mapAuditRow(row, sourceInstanceId));
}

export async function readAuditProjectionBounds(pool) {
  const result = await pool.request().query(`
    SET LOCK_TIMEOUT 2000;
    SET DEADLOCK_PRIORITY LOW;
    SELECT TOP (1)
      CONVERT(varchar(33), created_at, 126) AS created_at_cursor, event_id
    FROM audit.monitoring_http_event
    ORDER BY created_at,
      LOWER(CONVERT(char(36), event_id)) COLLATE Latin1_General_100_BIN2;

    SELECT TOP (1)
      CONVERT(varchar(33), created_at, 126) AS created_at_cursor, event_id
    FROM audit.monitoring_http_event
    ORDER BY created_at DESC,
      LOWER(CONVERT(char(36), event_id)) COLLATE Latin1_General_100_BIN2 DESC;
  `);
  return {
    retentionBoundary: mapCursor(result.recordsets.at(0)?.at(0)),
    frontier: mapCursor(result.recordsets.at(1)?.at(0))
  };
}

function mapCursor(row) {
  return row ? {
    createdAt: withUtcSuffix(row.created_at_cursor),
    eventId: row.event_id.toLowerCase()
  } : null;
}

function mapAuditRow(row, sourceInstanceId) {
  const createdAt = withUtcSuffix(row.created_at_cursor);
  const occurredAt = row.occurred_at.toISOString();
  const eventId = row.event_id.toLowerCase();
  const event = parseTelemetryEnvelope({
    eventId: `chatbackend:${sourceInstanceId}:${eventId}`,
    schemaVersion: 1,
    sourceType: 'CHATBACKEND_AUDIT',
    sourceInstanceId,
    eventName: 'http.request.completed',
    occurredAt,
    receivedAt: createdAt,
    severity: row.response_status >= 500 ? 'CRITICAL' : row.response_status >= 400 ? 'WARNING' : 'INFO',
    dimensions: compact({
      method: row.method,
      route: row.route_template,
      statusClass: `${Math.floor(row.response_status / 100)}xx`,
      responseCode: row.response_code,
      errorCode: row.error_code
    }),
    measurements: {
      durationMs: Number(row.duration_ms),
      persistenceDelayMs: Math.max(0, new Date(createdAt).getTime() - new Date(occurredAt).getTime())
    }
  });
  return { cursor: { createdAt, eventId }, event };
}

function withUtcSuffix(value) {
  return value.endsWith('Z') ? value : `${value}Z`;
}

function withoutUtcSuffix(value) {
  return value.endsWith('Z') ? value.slice(0, -1) : value;
}

function compact(value) {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== null && entry !== undefined));
}
