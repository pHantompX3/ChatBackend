import assert from 'node:assert/strict';
import test from 'node:test';
import { buildApplication } from '../../../src/server/app.js';
import { loadConfig } from '../../../src/server/config.js';

const logger = Object.freeze({
  debug() {},
  info() {},
  warn() {},
  error() {}
});

test('health and status APIs are bounded and set security headers', async (context) => {
  const application = await buildApplication({
    config: loadConfig({
      CHAT_MONITOR_ENVIRONMENTS_FILE: './test/fixtures/environments.json'
    }, process.cwd()),
    logger,
    clientDirectory: '/definitely-not-a-dashboard-directory'
  });
  context.after(() => application.close());

  const ready = await application.inject({ method: 'GET', url: '/api/v1/health/ready' });
  assert.equal(ready.statusCode, 200);
  assert.deepEqual(ready.json(), { status: 'UP' });
  assert.match(ready.headers['content-security-policy'], /default-src 'self'/);
  assert.equal(ready.headers['x-content-type-options'], 'nosniff');

  const environments = await application.inject({ method: 'GET', url: '/api/v1/environments' });
  assert.equal(environments.statusCode, 200);
  assert.equal(environments.json().defaultEnvironmentId, 'local-dev');
  assert.equal(environments.json().environments.length, 3);

  const status = await application.inject({ method: 'GET', url: '/api/v1/status' });
  assert.equal(status.statusCode, 200);
  assert.equal(status.json().sourceMode, 'fixture');
  assert.equal(status.json().schemaVersion, 2);
  assert.equal(status.json().environment.id, 'local-dev');

  const selected = await application.inject({
    method: 'GET',
    url: '/api/v1/environments/production/status'
  });
  assert.equal(selected.statusCode, 200);
  assert.equal(selected.json().environment.classification, 'production');
  assert.equal(selected.json().sourceMode, 'planned');
  assert.equal(selected.json().state, 'SOURCE_UNAVAILABLE');
  assert.equal(selected.json().errorCode, 'ENVIRONMENT_NOT_DEPLOYED');
  assert.equal(selected.json().retention.eventCount, 0);

  const unknown = await application.inject({
    method: 'GET',
    url: '/api/v1/environments/unknown/status'
  });
  assert.equal(unknown.statusCode, 404);
  assert.deepEqual(unknown.json(), { code: 'ENVIRONMENT_NOT_FOUND' });

  const missing = await application.inject({ method: 'GET', url: '/api/v1/missing' });
  assert.equal(missing.statusCode, 404);
  assert.deepEqual(missing.json(), { code: 'NOT_FOUND' });
});

test('operational APIs reject unsafe bounds and return sanitized data', async (context) => {
  const metricRepository = {
    overview: (_environmentId, hours) => {
      if (Number(hours) > 2160) throw new RangeError('value must be an integer between 1 and 2160');
      return { hours: Number(hours), requestCount: 1, failureCount: 0 };
    },
    timeSeries: () => ({ points: [] }),
    operations: () => ({ operations: [] }),
    failures: () => ({ failures: [] })
  };
  const runtime = {
    stateRepository: { get: () => ({ state: 'CURRENT' }) },
    eventRepository: { retainedRange: () => ({}), count: () => 0 },
    metricRepository,
    alertService: {
      evaluate() {},
      list: () => [],
      acknowledge: () => true,
      suppress: () => true
    }
  };
  const application = await buildApplication({
    config: loadConfig({ CHAT_MONITOR_ENVIRONMENTS_FILE: './test/fixtures/environments.json' }, process.cwd()),
    logger,
    runtime,
    clientDirectory: '/definitely-not-a-dashboard-directory'
  });
  context.after(() => application.close());

  const overview = await application.inject({
    method: 'GET', url: '/api/v1/environments/local-dev/overview?hours=24'
  });
  assert.equal(overview.statusCode, 200);
  assert.equal(overview.json().environmentId, 'local-dev');
  const invalid = await application.inject({
    method: 'GET', url: '/api/v1/environments/local-dev/overview?hours=99999'
  });
  assert.equal(invalid.statusCode, 400);
  assert.equal(invalid.json().code, 'INVALID_QUERY_BOUNDS');

  const crossOriginMutation = await application.inject({
    method: 'POST',
    url: '/api/v1/environments/local-dev/alerts/collector-health/acknowledge',
    headers: { origin: 'https://attacker.example' }
  });
  assert.equal(crossOriginMutation.statusCode, 403);

  const localMutation = await application.inject({
    method: 'POST',
    url: '/api/v1/environments/local-dev/alerts/collector-health/acknowledge',
    headers: {
      host: '127.0.0.1:8090',
      origin: 'http://127.0.0.1:8090',
      'x-chat-monitor-action': '1'
    }
  });
  assert.equal(localMutation.statusCode, 204);
});
