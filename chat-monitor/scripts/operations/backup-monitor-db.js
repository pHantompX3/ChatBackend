import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import Database from 'better-sqlite3';

const [sourcePath, destinationDirectory] = process.argv.slice(2);
if (!sourcePath || !destinationDirectory) {
  throw new Error('Usage: backup-monitor-db.js <database> <destination-directory>');
}

fs.mkdirSync(destinationDirectory, { recursive: true, mode: 0o700 });
const timestamp = new Date().toISOString().replaceAll(':', '').replaceAll('.', '-');
const destination = path.join(destinationDirectory, `chat-monitor-${timestamp}.sqlite`);
const database = new Database(sourcePath, { readonly: true });
try {
  await database.backup(destination);
} finally {
  database.close();
}

const verification = new Database(destination, { readonly: true, fileMustExist: true });
try {
  const result = verification.pragma('integrity_check', { simple: true });
  if (result !== 'ok') throw new Error(`SQLite integrity check failed: ${result}`);
} finally {
  verification.close();
}
fs.rmSync(`${destination}-shm`, { force: true });
fs.rmSync(`${destination}-wal`, { force: true });
const digest = createHash('sha256').update(fs.readFileSync(destination)).digest('hex');
fs.writeFileSync(`${destination}.sha256`, `${digest}  ${path.basename(destination)}\n`, { mode: 0o600 });
process.stdout.write(`${destination}\n`);
