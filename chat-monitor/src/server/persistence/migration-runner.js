import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';

const migrationNamePattern = /^(\d{3})_[a-z0-9_]+\.sql$/;

export function applyMigrations(database, migrationsDirectory) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS schema_migration (
      version INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      checksum TEXT NOT NULL,
      applied_at TEXT NOT NULL
    )
  `);

  const applied = new Map(
    database.prepare('SELECT version, name, checksum FROM schema_migration ORDER BY version').all()
      .map((migration) => [migration.version, migration])
  );

  const files = fs.readdirSync(migrationsDirectory).filter((name) => migrationNamePattern.test(name)).sort();
  for (const name of files) {
    const version = Number.parseInt(name.slice(0, 3), 10);
    const sql = fs.readFileSync(path.join(migrationsDirectory, name), 'utf8');
    const checksum = createHash('sha256').update(sql).digest('hex');
    const appliedMigration = applied.get(version);
    if (appliedMigration && appliedMigration.name !== name) {
      throw new Error(`SQLite migration ${version} was previously applied as ${appliedMigration.name}`);
    }
    if (appliedMigration && appliedMigration.checksum !== checksum) {
      throw new Error(`SQLite migration ${name} changed after it was applied`);
    }
    if (appliedMigration) {
      continue;
    }

    database.transaction(() => {
      database.exec(sql);
      database.prepare(
        'INSERT INTO schema_migration(version, name, checksum, applied_at) VALUES (?, ?, ?, ?)'
      ).run(version, name, checksum, new Date().toISOString());
    })();
  }
}
