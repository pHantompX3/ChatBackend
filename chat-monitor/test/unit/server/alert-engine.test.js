import assert from 'node:assert/strict';
import test from 'node:test';
import { ALERT_STATES, evaluateAlert } from '../../../src/server/alerting/alert-engine.js';

const active = Object.freeze({
  active: true,
  confirmations: 2,
  recoveryConfirmations: 2,
  detail: 'threshold exceeded'
});
const clear = Object.freeze({ ...active, active: false, detail: 'threshold clear' });

test('alert state requires confirmation, supports acknowledgement, and confirms recovery', () => {
  const first = evaluateAlert(undefined, active, new Date('2026-10-16T12:00:00Z'));
  assert.equal(first.state, ALERT_STATES.PENDING);
  const firing = evaluateAlert(first, active, new Date('2026-10-16T12:01:00Z'));
  assert.equal(firing.state, ALERT_STATES.FIRING);
  assert.equal(
    evaluateAlert(firing, active, new Date('2026-10-16T12:02:00Z')).state,
    ALERT_STATES.FIRING
  );

  const acknowledged = { ...firing, state: ALERT_STATES.ACKNOWLEDGED };
  assert.equal(evaluateAlert(acknowledged, active).state, ALERT_STATES.ACKNOWLEDGED);
  const clearing = evaluateAlert(acknowledged, clear);
  assert.equal(clearing.state, ALERT_STATES.ACKNOWLEDGED);
  const recovered = evaluateAlert(clearing, clear);
  assert.equal(recovered.state, ALERT_STATES.RECOVERED);
});
