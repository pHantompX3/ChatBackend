export const MONITOR_STATES = Object.freeze({
  CURRENT: 'CURRENT',
  CATCHING_UP: 'CATCHING_UP',
  SOURCE_UNAVAILABLE: 'SOURCE_UNAVAILABLE',
  COLLECTION_FAILED: 'COLLECTION_FAILED',
  HISTORY_GAP: 'HISTORY_GAP'
});

export function isMonitorState(value) {
  return Object.values(MONITOR_STATES).includes(value);
}
