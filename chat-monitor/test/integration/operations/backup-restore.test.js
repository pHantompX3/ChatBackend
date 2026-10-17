import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import test from 'node:test';
import Database from 'better-sqlite3';
import { openMonitorDatabase } from '../../../src/server/persistence/sqlite.js';

test('backup is checksummed and restores a valid independent SQLite store', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-monitor-backup-'));
  const source = path.join(directory, 'source.sqlite');
  const backups = path.join(directory, 'backups');
  const restored = path.join(directory, 'restored.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  openMonitorDatabase(source).close();

  const output = execFileSync(process.execPath, [
    'scripts/operations/backup-monitor-db.js', source, backups
  ], { encoding: 'utf8' }).trim();
  assert.equal(fs.existsSync(`${output}.sha256`), true);
  execFileSync(process.execPath, [
    'scripts/operations/restore-monitor-db.js', output, restored
  ]);
  const database = new Database(restored, { readonly: true });
  assert.equal(database.pragma('integrity_check', { simple: true }), 'ok');
  assert.equal(database.prepare('SELECT MAX(version) AS version FROM schema_migration').get().version, 3);
  database.close();
});
