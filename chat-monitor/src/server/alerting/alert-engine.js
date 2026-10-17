export const ALERT_STATES = Object.freeze({
  PENDING: 'PENDING',
  FIRING: 'FIRING',
  ACKNOWLEDGED: 'ACKNOWLEDGED',
  RECOVERED: 'RECOVERED'
});

export function evaluateAlert(previous, observation, now = new Date()) {
  const timestamp = now.toISOString();
  const base = previous ?? {
    state: ALERT_STATES.RECOVERED,
    pendingCount: 0,
    recoveryCount: 0,
    firstObservedAt: null,
    acknowledgedAt: null,
    suppressedUntil: null
  };

  if (observation.active) {
    const alreadyActive = [ALERT_STATES.FIRING, ALERT_STATES.ACKNOWLEDGED].includes(base.state);
    const pendingCount = alreadyActive
      ? base.pendingCount
      : base.state === ALERT_STATES.PENDING ? base.pendingCount + 1 : 1;
    const remainsAcknowledged = base.state === ALERT_STATES.ACKNOWLEDGED;
    return {
      ...base,
      state: remainsAcknowledged
        ? ALERT_STATES.ACKNOWLEDGED
        : alreadyActive || pendingCount >= observation.confirmations
          ? ALERT_STATES.FIRING
          : ALERT_STATES.PENDING,
      pendingCount,
      recoveryCount: 0,
      firstObservedAt: base.firstObservedAt ?? timestamp,
      lastObservedAt: timestamp,
      detail: observation.detail
    };
  }

  const recoveryCount = base.state === ALERT_STATES.RECOVERED ? 0 : base.recoveryCount + 1;
  const recovered = recoveryCount >= observation.recoveryConfirmations;
  return {
    ...base,
    state: recovered ? ALERT_STATES.RECOVERED : base.state,
    pendingCount: 0,
    recoveryCount: recovered ? 0 : recoveryCount,
    firstObservedAt: recovered ? null : base.firstObservedAt,
    acknowledgedAt: recovered ? null : base.acknowledgedAt,
    lastObservedAt: timestamp,
    detail: observation.detail
  };
}
