import { MONITOR_STATES } from '../../shared/monitor-states.js';

export function createStateRepository(database) {
  const read = database.prepare(`
    SELECT source_id AS sourceId, state, last_attempt_at AS lastAttemptAt,
      last_success_at AS lastSuccessfulCollectionAt, last_error_code AS lastErrorCode,
      lag_seconds AS lagSeconds, detail, updated_at AS updatedAt
    FROM collector_state WHERE source_id = ?
  `);
  const write = database.prepare(`
    INSERT INTO collector_state(
      source_id, state, last_attempt_at, last_success_at, last_error_code,
      lag_seconds, detail, updated_at
    ) VALUES (
      @sourceId, @state, @lastAttemptAt, @lastSuccessfulCollectionAt,
      @lastErrorCode, @lagSeconds, @detail, @updatedAt
    ) ON CONFLICT(source_id) DO UPDATE SET
      state = excluded.state,
      last_attempt_at = excluded.last_attempt_at,
      last_success_at = excluded.last_success_at,
      last_error_code = excluded.last_error_code,
      lag_seconds = excluded.lag_seconds,
      detail = excluded.detail,
      updated_at = excluded.updated_at
  `);
  const insertGap = database.prepare(`
    INSERT INTO history_gap(
      source_id, expected_created_at, expected_event_id,
      available_created_at, available_event_id, detected_at
    ) VALUES (?, ?, ?, ?, ?, ?)
  `);

  return Object.freeze({
    get(sourceId) {
      return read.get(sourceId) ?? {
        sourceId,
        state: MONITOR_STATES.CATCHING_UP,
        lastAttemptAt: null,
        lastSuccessfulCollectionAt: null,
        lastErrorCode: null,
        lagSeconds: null,
        detail: 'Collection has not completed yet.',
        updatedAt: null
      };
    },
    save(state) {
      write.run(state);
    },
    recordGap(sourceId, expected, available, detectedAt) {
      insertGap.run(
        sourceId,
        expected.createdAt,
        expected.eventId,
        available.createdAt,
        available.eventId,
        detectedAt.toISOString()
      );
    }
  });
}
