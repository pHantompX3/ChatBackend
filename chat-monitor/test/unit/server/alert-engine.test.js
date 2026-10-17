import assert from 'node:assert/strict';
import test from 'node:test';
import { ALERT_STATES, evaluateAlert } from '../../../src/server/alerting/alert-engine.js';
import { createAlertService } from '../../../src/server/alerting/alert-service.js';

const active = Object.freeze({
  active: true,
  confirmations: 2,
  recoveryConfirmations: 2,
  detail: 'threshold exceeded'
});

test('a firing alert suppressed at transition is delivered once suppression expires', () => {
  const alerts = new Map();
  let deliveries = 0;
  const alertRepository = {
    get: (_environmentId, ruleId) => alerts.get(ruleId),
    save: (alert) => alerts.set(alert.ruleId, alert),
    list: () => [...alerts.values()],
    acknowledge: () => false,
    suppress: () => false
  };
  alerts.set('collector-health', {
    environmentId: 'local',
    ruleId: 'collector-health',
    state: ALERT_STATES.FIRING,
    pendingCount: 1,
    recoveryCount: 0,
    firstObservedAt: '2026-10-16T10:00:00.000Z',
    lastObservedAt: '2026-10-16T10:00:00.000Z',
    acknowledgedAt: null,
    suppressedUntil: '2026-10-16T11:00:00.000Z',
    lastDeliveryStatus: 'SUPPRESSED',
    lastDeliveryError: null,
    detail: 'Collector state is COLLECTION_FAILED.'
  });
  const service = createAlertService({
    alertRepository,
    metricRepository: { overview: () => ({ requestCount: 0, failureCount: 0, failureRate: 0 }) },
    stateRepository: { get: () => ({ state: 'COLLECTION_FAILED' }) },
    logger: { warn: () => { deliveries += 1; } }
  });

  service.evaluate('local', new Date('2026-10-16T11:00:01.000Z'));
  assert.equal(deliveries, 1);
  assert.equal(alerts.get('collector-health').lastDeliveryStatus, 'DELIVERED_LOCAL_LOG');
  service.evaluate('local', new Date('2026-10-16T11:01:00.000Z'));
  assert.equal(deliveries, 1);
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
