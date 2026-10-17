import { MONITOR_STATES } from '../../shared/monitor-states.js';

export function createState({
  sourceId,
  state,
  now,
  previous,
  lagSeconds = null,
  errorCode = null,
  detail
}) {
  return {
    sourceId,
    state,
    lastAttemptAt: now.toISOString(),
    lastSuccessfulCollectionAt: state === MONITOR_STATES.CURRENT || state === MONITOR_STATES.CATCHING_UP
      ? now.toISOString()
      : previous.lastSuccessfulCollectionAt,
    lastErrorCode: errorCode,
    lagSeconds,
    detail,
    updatedAt: now.toISOString()
  };
}
