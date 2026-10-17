import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import Database from 'better-sqlite3';

const [backupPath, destinationPath] = process.argv.slice(2);
if (!backupPath || !destinationPath) {
  throw new Error('Usage: restore-monitor-db.js <verified-backup> <database>');
}
const checksumPath = `${backupPath}.sha256`;
if (!fs.existsSync(checksumPath)) throw new Error('Backup checksum file is missing');
const expected = fs.readFileSync(checksumPath, 'utf8').trim().split(/\s+/)[0];
const actual = createHash('sha256').update(fs.readFileSync(backupPath)).digest('hex');
if (expected !== actual) throw new Error('Backup checksum does not match');

const verification = new Database(backupPath, { readonly: true, fileMustExist: true });
try {
  const result = verification.pragma('integrity_check', { simple: true });
  if (result !== 'ok') throw new Error(`SQLite integrity check failed: ${result}`);
} finally {
  verification.close();
}

fs.mkdirSync(path.dirname(destinationPath), { recursive: true, mode: 0o700 });
if (fs.existsSync(destinationPath)) {
  fs.renameSync(destinationPath, `${destinationPath}.pre-restore-${Date.now()}`);
}
fs.copyFileSync(backupPath, destinationPath);
fs.chmodSync(destinationPath, 0o600);
process.stdout.write(`${destinationPath}\n`);
