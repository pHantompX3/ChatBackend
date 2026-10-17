import { loadConfig, publicConfig } from '../src/server/config.js';

try {
  const config = loadConfig();
  process.stdout.write(`${JSON.stringify({ valid: true, config: publicConfig(config) }, null, 2)}\n`);
} catch (error) {
  process.stderr.write(`${JSON.stringify({
    valid: false,
    error: error.name,
    issues: error.issues?.map(({ path, message }) => ({ path: path.join('.'), message }))
  }, null, 2)}\n`);
  process.exitCode = 1;
}
