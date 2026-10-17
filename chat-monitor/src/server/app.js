import path from 'node:path';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import Fastify from 'fastify';
import fastifyStatic from '@fastify/static';
import { registerHealthRoutes } from './api/health-routes.js';
import { registerStatusRoutes } from './api/status-routes.js';
import { registerMetricsRoutes } from './api/metrics-routes.js';
import { registerResponseHeaders } from './security/response-headers.js';

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const defaultClientDirectory = path.resolve(moduleDirectory, '../../dist/client');

export async function buildApplication({ config, logger, runtime, clientDirectory = defaultClientDirectory }) {
  const application = Fastify({
    logger: false,
    bodyLimit: 16 * 1024
  });

  await registerResponseHeaders(application);
  await registerHealthRoutes(application);
  await registerStatusRoutes(application, config, runtime);
  await registerMetricsRoutes(application, config, runtime);

  if (existsSync(clientDirectory)) {
    await application.register(fastifyStatic, {
      root: clientDirectory,
      wildcard: false,
      index: false
    });
    application.get('/', async (_request, reply) => reply.sendFile('index.html'));
  } else {
    application.get('/', async (_request, reply) => reply.code(503).send({
      code: 'DASHBOARD_NOT_BUILT',
      message: 'Run npm run build before starting the production-mode service.'
    }));
  }

  application.setNotFoundHandler(async (request, reply) => {
    if (request.url.startsWith('/api/')) {
      return reply.code(404).send({ code: 'NOT_FOUND' });
    }
    return reply.code(404).send({ code: 'DASHBOARD_ROUTE_NOT_FOUND' });
  });

  application.addHook('onResponse', async (request, reply) => {
    logger.info('request_completed', {
      method: request.method,
      route: request.routeOptions.url ?? 'unmatched',
      statusCode: reply.statusCode,
      durationMs: reply.elapsedTime
    });
  });

  return application;
}
