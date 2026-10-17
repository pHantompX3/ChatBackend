import assert from 'node:assert/strict';
import test from 'node:test';
import { Writable } from 'node:stream';
import { createLogger } from '../../../src/server/observability/logger.js';

test('structured logger redacts sensitive fields recursively', () => {
  let output = '';
  const stream = new Writable({
    write(chunk, _encoding, callback) {
      output += chunk.toString();
      callback();
    }
  });

  createLogger('info', stream).info('example', {
    password: 'do-not-log',
    nested: { authorization: 'Bearer do-not-log' },
    route: '/api/v1/status'
  });

  const entry = JSON.parse(output);
  assert.equal(entry.password, '[REDACTED]');
  assert.equal(entry.nested.authorization, '[REDACTED]');
  assert.equal(entry.route, '/api/v1/status');
  assert.equal(output.includes('do-not-log'), false);
});
