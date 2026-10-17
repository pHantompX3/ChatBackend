import assert from 'node:assert/strict';
import test from 'node:test';
import { parseTelemetryEnvelope } from '../../../src/shared/telemetry-envelope.js';

const validEvent = Object.freeze({
  eventId: 'fixture:event:1',
  schemaVersion: 1,
  sourceType: 'CHATBACKEND_AUDIT',
  sourceInstanceId: 'fixture',
  eventName: 'http.request.completed',
  occurredAt: '2026-10-16T12:00:00.000Z',
  receivedAt: '2026-10-16T12:00:01.000Z',
  severity: 'INFO',
  dimensions: { method: 'GET', route: '/api/v1/ping', statusClass: '2xx' },
  measurements: { durationMs: 12 }
});

test('normalized telemetry accepts the bounded source-neutral contract', () => {
  assert.equal(parseTelemetryEnvelope(validEvent).eventId, validEvent.eventId);
});

test('normalized telemetry rejects unknown versions, events, and content-bearing dimensions', () => {
  assert.throws(() => parseTelemetryEnvelope({ ...validEvent, schemaVersion: 2 }));
  assert.throws(() => parseTelemetryEnvelope({ ...validEvent, eventName: 'arbitrary.event' }), /Unsupported/);
  assert.throws(
    () => parseTelemetryEnvelope({ ...validEvent, dimensions: { ...validEvent.dimensions, messageBody: 'private' } }),
    /Unsupported dimension/
  );
});

test('normalized telemetry rejects oversized values before persistence', () => {
  assert.throws(
    () => parseTelemetryEnvelope({
      ...validEvent,
      dimensions: { ...validEvent.dimensions, route: `/${'x'.repeat(161)}` }
    })
  );
});
