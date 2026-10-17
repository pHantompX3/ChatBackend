import { spawn } from 'node:child_process';

const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const children = [
  spawn(npmCommand, ['start'], { stdio: 'inherit', env: process.env }),
  spawn(npmCommand, ['exec', '--', 'vite'], { stdio: 'inherit', env: process.env })
];

let stopping = false;

function stop(exitCode = 0) {
  if (stopping) {
    return;
  }
  stopping = true;
  for (const child of children) {
    child.kill('SIGTERM');
  }
  process.exitCode = exitCode;
}

for (const child of children) {
  child.once('exit', (code, signal) => {
    if (!stopping && (code !== 0 || signal)) {
      stop(code ?? 1);
    }
  });
}

process.once('SIGINT', () => stop());
process.once('SIGTERM', () => stop());
