import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { openMonitorDatabase } from '../../../src/server/persistence/sqlite.js';

test('environment partition migration preserves the legacy fixture history', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-monitor-migration-'));
  const legacyMigrations = path.join(directory, 'migrations');
  const databasePath = path.join(directory, 'monitor.sqlite');
  fs.mkdirSync(legacyMigrations);
  fs.copyFileSync(
    path.resolve('migrations/sqlite/001_initial_monitor_store.sql'),
    path.join(legacyMigrations, '001_initial_monitor_store.sql')
  );
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));

  const legacy = openMonitorDatabase(databasePath, { migrationsDirectory: legacyMigrations });
  legacy.prepare(`
    INSERT INTO projected_event(
      event_id, schema_version, source_type, source_instance_id, event_name,
      occurred_at, received_at, severity, dimensions_json, measurements_json,
      source_created_at, source_event_id, inserted_at
    ) VALUES (?, 1, 'SYNTHETIC', 'fixture', 'http.request.completed', ?, ?, 'INFO', '{}', '{}', ?, ?, ?)
  `).run('legacy-event', ...Array(5).fill('2026-10-16T12:00:00.000Z'));
  legacy.close();

  const upgraded = openMonitorDatabase(databasePath);
  context.after(() => upgraded.close());
  const row = upgraded.prepare(
    'SELECT environment_id AS environmentId, event_id AS eventId FROM projected_event'
  ).get();
  assert.deepEqual(row, { environmentId: 'fixture', eventId: 'legacy-event' });
  assert.equal(
    upgraded.prepare('SELECT MAX(version) AS version FROM schema_migration').get().version,
    3
  );
});
