import { ALERT_STATES, evaluateAlert } from './alert-engine.js';

const RULES = Object.freeze([
  {
    id: 'collector-health',
    confirmations: 1,
    recoveryConfirmations: 2,
    observe: ({ collectorState }) => ({
      active: ['SOURCE_UNAVAILABLE', 'COLLECTION_FAILED', 'HISTORY_GAP'].includes(collectorState.state),
      detail: collectorState.state === 'CURRENT'
        ? 'Collector is current.'
        : `Collector state is ${collectorState.state}.`
    })
  },
  {
    id: 'http-failure-rate',
    confirmations: 2,
    recoveryConfirmations: 2,
    observe: ({ overview }) => ({
      active: overview.requestCount >= 5 && overview.failureRate >= 0.1,
      detail: `${overview.failureCount} server failures across ${overview.requestCount} requests in the selected window.`
    })
  }
]);

export function createAlertService({ alertRepository, metricRepository, stateRepository, logger }) {
  return Object.freeze({
    evaluate(environmentId, now = new Date()) {
      const context = {
        collectorState: stateRepository.get(environmentId),
        overview: metricRepository.overview(environmentId, 1, now)
      };
      return RULES.map((rule) => {
        const previous = alertRepository.get(environmentId, rule.id);
        const observation = {
          ...rule.observe(context),
          confirmations: rule.confirmations,
          recoveryConfirmations: rule.recoveryConfirmations
        };
        const evaluated = evaluateAlert(previous, observation, now);
        const changed = previous?.state !== evaluated.state;
        const suppressed = evaluated.suppressedUntil && new Date(evaluated.suppressedUntil) > now;
        const shouldDeliver = evaluated.state === ALERT_STATES.FIRING
          && !suppressed
          && (changed || previous?.lastDeliveryStatus === 'SUPPRESSED');
        const deliveryStatus = shouldDeliver
          ? deliver(logger, environmentId, rule.id, evaluated)
          : evaluated.state === ALERT_STATES.FIRING && suppressed
            ? 'SUPPRESSED'
            : previous?.lastDeliveryStatus ?? 'NOT_ATTEMPTED';
        const saved = {
          environmentId,
          ruleId: rule.id,
          ...evaluated,
          lastDeliveryStatus: deliveryStatus,
          lastDeliveryError: null
        };
        alertRepository.save(saved);
        return saved;
      });
    },
    list(environmentId) {
      return alertRepository.list(environmentId);
    },
    acknowledge(environmentId, ruleId, now = new Date()) {
      return alertRepository.acknowledge(environmentId, ruleId, now);
    },
    suppress(environmentId, ruleId, hours, now = new Date()) {
      const boundedHours = Number(hours);
      if (!Number.isInteger(boundedHours) || boundedHours < 1 || boundedHours > 24) {
        throw new RangeError('suppression hours must be an integer between 1 and 24');
      }
      return alertRepository.suppress(
        environmentId,
        ruleId,
        new Date(now.getTime() + boundedHours * 60 * 60 * 1000),
        now
      );
    }
  });
}

function deliver(logger, environmentId, ruleId, alert) {
  logger.warn('monitor_alert_firing', {
    environmentId,
    ruleId,
    state: alert.state,
    observedAt: alert.lastObservedAt
  });
  return 'DELIVERED_LOCAL_LOG';
}
