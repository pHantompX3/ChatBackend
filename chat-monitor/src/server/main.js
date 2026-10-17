import { ZodError } from 'zod';
import { buildApplication } from './app.js';
import { loadConfig, publicConfig } from './config.js';
import { createLogger } from './observability/logger.js';
import { createMonitorRuntime } from './runtime.js';

let application;
let logger;
let runtime;

async function shutdown(signal) {
  logger?.info('shutdown_started', { signal });
  if (application) {
    await application.close();
  }
  if (runtime) {
    await runtime.close();
  }
  logger?.info('shutdown_completed');
}

async function start() {
  const config = loadConfig();
  logger = createLogger(config.logLevel);
  runtime = createMonitorRuntime(config, logger);
  application = await buildApplication({ config, logger, runtime });

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.once(signal, () => {
      shutdown(signal)
        .then(() => process.exit(0))
        .catch((error) => {
          logger.error('shutdown_failed', { errorName: error.name, errorMessage: error.message });
          process.exit(1);
        });
    });
  }

  await application.listen({ host: config.host, port: config.port });
  await runtime.start();
  logger.info('chat_monitor_started', publicConfig(config));
}

start().catch((error) => {
  const safeDetails = error instanceof ZodError
    ? { issues: error.issues.map(({ path, message }) => ({ path: path.join('.'), message })) }
    : { errorName: error.name, errorMessage: error.message };
  (logger ?? createLogger('error', process.stderr)).error('startup_failed', safeDetails);
  process.exitCode = 1;
});
