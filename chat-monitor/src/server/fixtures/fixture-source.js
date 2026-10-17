import fs from 'node:fs';
import { parseTelemetryEnvelope } from '../../shared/telemetry-envelope.js';
import { compareCursors } from '../collector/cursor.js';

export function createFixtureSource(fixturePath) {
  const document = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
  if (document.schemaVersion !== 1 || !Array.isArray(document.events)) {
    throw new Error('Unsupported fixture document');
  }

  const records = document.events
    .map((value) => {
      const event = parseTelemetryEnvelope(value);
      return {
        cursor: { createdAt: event.receivedAt, eventId: event.eventId },
        event
      };
    })
    .sort((left, right) => compareCursors(left.cursor, right.cursor));

  return Object.freeze({
    sourceId: 'fixture',
    async readBounds() {
      return {
        retentionBoundary: records.at(0)?.cursor ?? null,
        frontier: records.at(-1)?.cursor ?? null
      };
    },
    async readPage(after, limit) {
      return records.filter((record) => compareCursors(record.cursor, after) > 0).slice(0, limit);
    },
    async close() {}
  });
}
