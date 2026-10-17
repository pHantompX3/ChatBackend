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
const displacedPath = `${destinationPath}.pre-restore-${Date.now()}`;
const displaced = [];
for (const suffix of ['', '-wal', '-shm']) {
  const source = `${destinationPath}${suffix}`;
  if (fs.existsSync(source)) {
    const target = `${displacedPath}${suffix}`;
    fs.renameSync(source, target);
    displaced.push({ source, target });
  }
}
try {
  fs.copyFileSync(backupPath, destinationPath);
  fs.chmodSync(destinationPath, 0o600);
} catch (error) {
  if (fs.existsSync(destinationPath)) fs.rmSync(destinationPath);
  for (const { source, target } of displaced) fs.renameSync(target, source);
  throw error;
}
process.stdout.write(`${destinationPath}\n`);
