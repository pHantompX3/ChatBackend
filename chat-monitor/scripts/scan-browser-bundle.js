import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const outputDirectory = path.resolve('dist/client');
const prohibitedPatterns = [
  /CHAT_MONITOR_SOURCE_PASSWORD/,
  /CHAT_MONITOR_SOURCE_USERNAME/,
  /CHAT_MONITOR_SOURCE_HOST/,
  /wl_chat_monitor/,
  /Application Support\/ChatMonitor/,
  /BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY/
];

async function filesBelow(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(entries.map((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(target) : [target];
  }));
  return files.flat();
}

const files = await filesBelow(outputDirectory);
for (const file of files) {
  const content = await readFile(file, 'utf8');
  for (const pattern of prohibitedPatterns) {
    if (pattern.test(content)) {
      throw new Error(`Browser bundle contains prohibited server configuration in ${path.relative(outputDirectory, file)}`);
    }
  }
}

process.stdout.write(`Scanned ${files.length} browser assets; no prohibited server configuration found.\n`);
