import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createMetricRepository } from '../../../src/server/persistence/metric-repository.js';
import { openMonitorDatabase } from '../../../src/server/persistence/sqlite.js';

test('metric repository separates expected client outcomes from server failures and bounds input', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-monitor-metrics-'));
  const database = openMonitorDatabase(path.join(directory, 'monitor.sqlite'));
  context.after(() => {
    database.close();
    fs.rmSync(directory, { recursive: true, force: true });
  });
  const insert = database.prepare(`
    INSERT INTO metric_bucket(environment_id, resolution, bucket_start, event_name, method, route,
      status_class, request_count, failure_count, duration_count, duration_sum, duration_min, duration_max)
    VALUES ('local-dev', 'hour', '2026-10-16T12:00:00.000Z', 'http.request.completed',
      'GET', '/api/v1/test', ?, 1, ?, 1, ?, ?, ?)
  `);
  insert.run('2xx', 0, 10, 10, 10);
  insert.run('4xx', 0, 20, 20, 20);
  insert.run('5xx', 1, 30, 30, 30);

  const repository = createMetricRepository(database);
  const now = new Date('2026-10-16T13:00:00.000Z');
  assert.deepEqual(repository.overview('local-dev', 24, now), {
    hours: 24,
    generatedAt: now.toISOString(),
    requestCount: 3,
    failureCount: 1,
    expected4xxCount: 1,
    failureRate: 1 / 3,
    averageDurationMs: 20,
    maximumDurationMs: 30
  });
  assert.throws(() => repository.overview('local-dev', 0, now), /between 1 and 2160/);
});

test('rolling windows retain the partially overlapping metric bucket', (context) => {
  const database = openMonitorDatabase(':memory:');
  context.after(() => database.close());
  database.prepare(`
    INSERT INTO metric_bucket(environment_id, resolution, bucket_start, event_name, method, route,
      status_class, request_count, failure_count, duration_count, duration_sum, duration_min, duration_max)
    VALUES ('local-dev', 'hour', '2026-10-16T12:00:00.000Z', 'http.request.completed',
      'GET', '/api/v1/ping', '5xx', 1, 1, 1, 25, 25, 25)
  `).run();

  const overview = createMetricRepository(database).overview(
    'local-dev',
    1,
    new Date('2026-10-16T13:00:01.000Z')
  );
  assert.equal(overview.requestCount, 1);
  assert.equal(overview.failureCount, 1);
});
