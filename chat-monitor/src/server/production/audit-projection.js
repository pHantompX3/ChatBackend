import sql from 'mssql';
import { parseTelemetryEnvelope } from '../../shared/telemetry-envelope.js';

export async function readAuditProjectionPage(pool, after, limit, sourceInstanceId) {
  const request = pool.request();
  request.input('createdAt', sql.DateTime2, new Date(after.createdAt));
  request.input('eventId', sql.UniqueIdentifier, after.eventId);
  request.input('pageSize', sql.Int, limit);
  const result = await request.query(`
    SET LOCK_TIMEOUT 2000;
    SET DEADLOCK_PRIORITY LOW;
    SELECT TOP (@pageSize)
      event_id, created_at, occurred_at, operation, method, route_template,
      response_status, response_code, duration_ms, error_code
    FROM audit.monitoring_http_event
    WHERE created_at > @createdAt OR (created_at = @createdAt AND event_id > @eventId)
    ORDER BY created_at, event_id;
  `);

  return result.recordset.map((row) => mapAuditRow(row, sourceInstanceId));
}

export async function readAuditProjectionBounds(pool) {
  const result = await pool.request().query(`
    SET LOCK_TIMEOUT 2000;
    SET DEADLOCK_PRIORITY LOW;
    SELECT TOP (1) created_at, event_id
    FROM audit.monitoring_http_event
    ORDER BY created_at, event_id;

    SELECT TOP (1) created_at, event_id
    FROM audit.monitoring_http_event
    ORDER BY created_at DESC, event_id DESC;
  `);
  return {
    retentionBoundary: mapCursor(result.recordsets.at(0)?.at(0)),
    frontier: mapCursor(result.recordsets.at(1)?.at(0))
  };
}

function mapCursor(row) {
  return row ? { createdAt: row.created_at.toISOString(), eventId: row.event_id.toLowerCase() } : null;
}

function mapAuditRow(row, sourceInstanceId) {
  const createdAt = row.created_at.toISOString();
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

function compact(value) {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== null && entry !== undefined));
}
