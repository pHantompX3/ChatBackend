import assert from 'node:assert/strict';
import test from 'node:test';
import { createScheduler } from '../../../src/server/collector/scheduler.js';

const logger = Object.freeze({ error() {} });

test('scheduler never overlaps collection cycles and waits during shutdown', async () => {
  let release;
  let calls = 0;
  const blocked = new Promise((resolve) => { release = resolve; });
  const scheduler = createScheduler({
    collector: {
      async collect() {
        calls += 1;
        await blocked;
      }
    },
    intervalSeconds: 60,
    logger
  });

  const first = scheduler.runNow();
  const second = scheduler.runNow();
  assert.equal(calls, 1);
  assert.equal(first, second);

  const stopping = scheduler.stop();
  release();
  await stopping;
  assert.equal(calls, 1);
});

test('scheduler runs the post-cycle hook after successful and failed collection', async () => {
  let hookCalls = 0;
  let collectionCalls = 0;
  const scheduler = createScheduler({
    collector: {
      async collect() {
        collectionCalls += 1;
        if (collectionCalls === 2) throw new Error('synthetic collection failure');
      }
    },
    environmentId: 'local-dev',
    intervalSeconds: 60,
    logger: { error() {} },
    afterCycle() { hookCalls += 1; }
  });

  await scheduler.start();
  await scheduler.runNow();
  await scheduler.stop();

  assert.equal(collectionCalls, 2);
  assert.equal(hookCalls, 2);
});
