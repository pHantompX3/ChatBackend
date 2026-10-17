import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';
import { applyMigrations } from './migration-runner.js';

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const defaultMigrationsDirectory = path.resolve(moduleDirectory, '../../../migrations/sqlite');

export function openMonitorDatabase(databasePath, { migrationsDirectory = defaultMigrationsDirectory } = {}) {
  fs.mkdirSync(path.dirname(databasePath), { recursive: true, mode: 0o700 });
  const database = new Database(databasePath);
  try {
    database.pragma('journal_mode = WAL');
    database.pragma('foreign_keys = ON');
    database.pragma('busy_timeout = 5000');
    database.pragma('synchronous = FULL');
    applyMigrations(database, migrationsDirectory);
    return database;
  } catch (error) {
    database.close();
    throw error;
  }
}
