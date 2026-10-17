import assert from 'node:assert/strict';
import test from 'node:test';
import { createAggregator } from '../../../src/server/aggregation/aggregator.js';
import { createRetentionService } from '../../../src/server/aggregation/retention.js';
import { createCollector } from '../../../src/server/collector/collector.js';
import { compareCursors } from '../../../src/server/collector/cursor.js';
import { SourceUnavailableError } from '../../../src/server/collector/source-error.js';
import { createCursorRepository } from '../../../src/server/persistence/cursor-repository.js';
import { createEventRepository } from '../../../src/server/persistence/event-repository.js';
import { openMonitorDatabase } from '../../../src/server/persistence/sqlite.js';
import { createStateRepository } from '../../../src/server/persistence/state-repository.js';

const logger = Object.freeze({ info() {}, warn() {}, error() {} });
const now = new Date('2026-10-16T13:00:00.000Z');

test('collector commits bounded pages, aggregates once, and resumes idempotently', async (context) => {
  const harness = createHarness(context, records(5), { pageSize: 2 });

  const first = await harness.collector.collect();
  assert.deepEqual(first, { state: 'CURRENT', pages: 3, inserted: 5 });
  assert.equal(harness.events.count('test-environment'), 5);
  assert.equal(harness.database.prepare('SELECT SUM(request_count) AS count FROM metric_bucket WHERE environment_id = ? AND resolution = ?').get('test-environment', 'hour').count, 5);

  const second = await harness.collector.collect();
  assert.deepEqual(second, { state: 'CURRENT', pages: 0, inserted: 0 });
  assert.equal(harness.events.count('test-environment'), 5);
  assert.equal(harness.database.prepare('SELECT SUM(request_count) AS count FROM metric_bucket WHERE environment_id = ? AND resolution = ?').get('test-environment', 'hour').count, 5);
  assert.equal(harness.cursors.get('test-environment').eventId, '00000000-0000-0000-0000-000000000005');
  assert.equal(harness.states.get('test-environment').lagSeconds, 0);
});

test('collector rolls back event and cursor together, then retries without duplicates', async (context) => {
  const database = openMonitorDatabase(':memory:');
  context.after(() => database.close());
  const source = createSource(records(1));
  const events = createEventRepository(database);
  const cursors = createCursorRepository(database);
  const states = createStateRepository(database);
  const failing = collector(database, source, events, cursors, states, { record() { throw new Error('synthetic failure'); } });

  assert.equal((await failing.collect()).state, 'COLLECTION_FAILED');
  assert.equal(events.count('test-environment'), 0);
  assert.equal(cursors.get('test-environment').initialized, false);

  const retry = collector(database, source, events, cursors, states, createAggregator(database));
  assert.equal((await retry.collect()).state, 'CURRENT');
  assert.equal(events.count('test-environment'), 1);
});

test('collector reports a source outage, then recovers without losing its cursor', async (context) => {
  let available = false;
  const recoveredRecords = records(1);
  const unavailableHarness = createHarness(context, [], {
    source: {
      sourceId: 'unavailable',
      async readBounds() {
        if (!available) throw new SourceUnavailableError('offline');
        return { retentionBoundary: recoveredRecords[0].cursor, frontier: recoveredRecords[0].cursor };
      },
      async readPage(after, limit) {
        return recoveredRecords.filter((record) => compareCursors(record.cursor, after) > 0).slice(0, limit);
      },
      async close() {}
    }
  });
  assert.equal((await unavailableHarness.collector.collect()).state, 'SOURCE_UNAVAILABLE');
  available = true;
  assert.equal((await unavailableHarness.collector.collect()).state, 'CURRENT');
  assert.equal(unavailableHarness.events.count('test-environment'), 1);
});

test('collector reports retained-history gaps explicitly', async (context) => {
  const gapHarness = createHarness(context, records(1, 10));
  gapHarness.cursors.save('test-environment', {
    createdAt: '2026-10-16T11:00:00.000Z',
    eventId: '00000000-0000-0000-0000-000000000001'
  }, now);
  const recovered = await gapHarness.collector.collect();
  assert.equal(recovered.state, 'CURRENT');
  assert.equal(recovered.inserted, 1);
  assert.equal(gapHarness.database.prepare('SELECT COUNT(*) AS count FROM history_gap').get().count, 1);
  assert.equal((await gapHarness.collector.collect()).inserted, 0);
  assert.equal(gapHarness.database.prepare('SELECT COUNT(*) AS count FROM history_gap').get().count, 1);
});

test('non-HTTP events are retained without contaminating HTTP request metrics', async (context) => {
  const sourceRecords = records(1);
  sourceRecords.push({
    cursor: {
      createdAt: '2026-10-16T12:02:00.000Z',
      eventId: '00000000-0000-0000-0000-000000000002'
    },
    event: {
      ...sourceRecords[0].event,
      eventId: 'client:test:2',
      eventName: 'client.recovery.completed',
      occurredAt: '2026-10-16T12:01:30.000Z',
      receivedAt: '2026-10-16T12:02:00.000Z',
      dimensions: { clientType: 'synthetic' }
    }
  });
  const harness = createHarness(context, sourceRecords);

  assert.equal((await harness.collector.collect()).inserted, 2);
  assert.equal(harness.events.count('test-environment'), 2);
  assert.equal(
    harness.database.prepare(
      'SELECT SUM(request_count) AS count FROM metric_bucket WHERE environment_id = ? AND resolution = ?'
    ).get('test-environment', 'hour').count,
    1
  );
});

test('collector pages by durable creation order even when an event occurred earlier', async (context) => {
  const delayed = records(2);
  delayed[1].event.occurredAt = '2026-10-15T12:00:00.000Z';
  const harness = createHarness(context, delayed, { pageSize: 1 });

  assert.equal((await harness.collector.collect()).state, 'CURRENT');
  assert.equal(harness.events.count('test-environment'), 2);
  assert.equal(harness.cursors.get('test-environment').eventId, delayed[1].cursor.eventId);
});

test('identical source events and aggregates remain isolated by environment', async (context) => {
  const database = openMonitorDatabase(':memory:');
  context.after(() => database.close());
  const events = createEventRepository(database);
  const cursors = createCursorRepository(database);
  const states = createStateRepository(database);
  const aggregator = createAggregator(database);
  const retention = createRetentionService(database, { detailRetentionDays: 90, aggregateRetentionDays: 365 });
  const sourceRecords = records(2);
  const development = collector(
    database, createSource(sourceRecords), events, cursors, states, aggregator, 2, retention, 'development'
  );
  const production = collector(
    database, createSource(sourceRecords), events, cursors, states, aggregator, 2, retention, 'production'
  );

  assert.equal((await development.collect()).inserted, 2);
  assert.equal((await production.collect()).inserted, 2);
  assert.equal(events.count('development'), 2);
  assert.equal(events.count('production'), 2);
  assert.equal(database.prepare('SELECT COUNT(*) AS count FROM projected_event').get().count, 4);
  assert.equal(database.prepare(
    'SELECT SUM(request_count) AS count FROM metric_bucket WHERE environment_id = ? AND resolution = ?'
  ).get('development', 'hour').count, 2);
  assert.equal(database.prepare(
    'SELECT SUM(request_count) AS count FROM metric_bucket WHERE environment_id = ? AND resolution = ?'
  ).get('production', 'hour').count, 2);
});

test('retention keeps the configured detailed and aggregate floors', (context) => {
  const harness = createHarness(context, []);
  harness.database.prepare(`
    INSERT INTO projected_event(
      environment_id, event_id, schema_version, source_type, source_instance_id, event_name,
      occurred_at, received_at, severity, dimensions_json, measurements_json,
      source_created_at, source_event_id, inserted_at
    ) VALUES ('test-environment', 'old', 1, 'SYNTHETIC', 'test', 'http.request.completed', ?, ?, 'INFO', '{}', '{}', ?, 'old', ?)
  `).run('2026-07-01T00:00:00.000Z', '2026-07-01T00:00:00.000Z', '2026-07-01T00:00:00.000Z', '2026-07-01T00:00:00.000Z');
  harness.database.prepare(`
    INSERT INTO metric_bucket(
      environment_id, resolution, bucket_start, event_name, method, route, status_class,
      request_count, failure_count, duration_count, duration_sum, duration_min, duration_max
    ) VALUES ('test-environment', 'day', '2025-01-01T00:00:00.000Z', 'http.request.completed', 'GET', '/api/v1/ping', '2xx', 1, 0, 1, 1, 1, 1)
  `).run();

  const result = harness.retention.apply(now);
  assert.deepEqual(result, { deletedEvents: 1, deletedBuckets: 1 });
});

function createHarness(context, sourceRecords, options = {}) {
  const database = openMonitorDatabase(':memory:');
  context.after(() => database.close());
  const source = options.source ?? createSource(sourceRecords);
  const events = createEventRepository(database);
  const cursors = createCursorRepository(database);
  const states = createStateRepository(database);
  const retention = createRetentionService(database, { detailRetentionDays: 90, aggregateRetentionDays: 365 });
  return {
    database,
    events,
    cursors,
    states,
    retention,
    collector: collector(database, source, events, cursors, states, createAggregator(database), options.pageSize, retention)
  };
}

function collector(
  database,
  source,
  events,
  cursors,
  states,
  aggregator,
  pageSize = 2,
  retention,
  environmentId = 'test-environment'
) {
  return createCollector({
    database,
    source,
    environmentId,
    eventRepository: events,
    cursorRepository: cursors,
    stateRepository: states,
    aggregator,
    retention: retention ?? createRetentionService(database, { detailRetentionDays: 90, aggregateRetentionDays: 365 }),
    pageSize,
    logger,
    clock: () => now
  });
}

function createSource(sourceRecords) {
  const ordered = [...sourceRecords].sort((left, right) => compareCursors(left.cursor, right.cursor));
  return {
    sourceId: 'test-source',
    async readBounds() {
      return {
        retentionBoundary: ordered.at(0)?.cursor ?? null,
        frontier: ordered.at(-1)?.cursor ?? null
      };
    },
    async readPage(after, limit) {
      return ordered.filter((record) => compareCursors(record.cursor, after) > 0).slice(0, limit);
    },
    async close() {}
  };
}

function records(count, start = 1) {
  return Array.from({ length: count }, (_, index) => {
    const number = start + index;
    const eventId = `00000000-0000-0000-0000-${String(number).padStart(12, '0')}`;
    const occurredAt = new Date(Date.UTC(2026, 9, 16, 12, 0, number)).toISOString();
    const createdAt = new Date(Date.UTC(2026, 9, 16, 12, 1, number)).toISOString();
    return {
      cursor: { createdAt, eventId },
      event: {
        eventId: `chatbackend:test:${eventId}`,
        schemaVersion: 1,
        sourceType: 'CHATBACKEND_AUDIT',
        sourceInstanceId: 'test',
        eventName: 'http.request.completed',
        occurredAt,
        receivedAt: createdAt,
        severity: 'INFO',
        dimensions: { method: 'GET', route: '/api/v1/ping', statusClass: '2xx' },
        measurements: { durationMs: number }
      }
    };
  });
}
