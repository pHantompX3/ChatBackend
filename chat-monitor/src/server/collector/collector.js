import { MONITOR_STATES } from '../../shared/monitor-states.js';
import { compareCursors, lastCursor } from './cursor.js';
import { createState } from './collection-state.js';
import { SourceUnavailableError } from './source-error.js';

export function createCollector({
  database,
  source,
  eventRepository,
  cursorRepository,
  stateRepository,
  aggregator,
  retention,
  environmentId,
  pageSize,
  maxPagesPerCycle = 20,
  logger,
  clock = () => new Date()
}) {
  const persistPage = database.transaction((records, cursor, now) => {
    let inserted = 0;
    for (const record of records) {
      if (eventRepository.insert(environmentId, record, now)) {
        aggregator.record(environmentId, record.event);
        inserted += 1;
      }
    }
    cursorRepository.save(environmentId, cursor, now);
    return inserted;
  });

  return Object.freeze({
    async collect() {
      const startedAt = clock();
      let cursor = cursorRepository.get(environmentId);
      let pages = 0;
      let inserted = 0;

      try {
        const { retentionBoundary, frontier } = await source.readBounds();
        if (
          cursor.initialized
          && retentionBoundary
          && compareCursors(cursor, retentionBoundary) < 0
        ) {
          stateRepository.recordGap(environmentId, cursor, retentionBoundary, startedAt);
          cursor = {
            createdAt: retentionBoundary.createdAt,
            eventId: '',
            initialized: true
          };
          cursorRepository.save(environmentId, cursor, startedAt);
          stateRepository.save(createState({
            sourceId: environmentId,
            state: MONITOR_STATES.HISTORY_GAP,
            now: startedAt,
            previous: stateRepository.get(environmentId),
            errorCode: 'SOURCE_RETENTION_GAP',
            detail: 'A source retention gap was recorded; collection resumed at the oldest retained timestamp.'
          }));
        }

        while (pages < maxPagesPerCycle) {
          const records = await source.readPage(cursor, pageSize);

          if (records.length === 0) {
            const current = saveSuccessState(MONITOR_STATES.CURRENT, cursor, frontier, startedAt);
            retention.apply(startedAt);
            return { state: current.state, pages, inserted };
          }

          const nextCursor = lastCursor(records, cursor);
          inserted += persistPage(records, nextCursor, startedAt);
          cursor = { ...nextCursor, initialized: true };
          pages += 1;

          if (records.length < pageSize) {
            const current = saveSuccessState(MONITOR_STATES.CURRENT, cursor, frontier, startedAt);
            retention.apply(startedAt);
            return { state: current.state, pages, inserted };
          }
        }

        const catchingUp = saveSuccessState(MONITOR_STATES.CATCHING_UP, cursor, frontier, startedAt);
        retention.apply(startedAt);
        return { state: catchingUp.state, pages, inserted };
      } catch (error) {
        const unavailable = error instanceof SourceUnavailableError;
        const state = createState({
          sourceId: environmentId,
          state: unavailable ? MONITOR_STATES.SOURCE_UNAVAILABLE : MONITOR_STATES.COLLECTION_FAILED,
          now: startedAt,
          previous: stateRepository.get(environmentId),
          errorCode: unavailable ? error.code : 'COLLECTION_CYCLE_FAILED',
          detail: unavailable
            ? 'The telemetry source could not be reached.'
            : 'The collection cycle failed before it could commit safely.'
        });
        try {
          stateRepository.save(state);
        } catch (stateError) {
          logger.error('collector_state_persistence_failed', {
            errorName: stateError.name,
            errorMessage: stateError.message
          });
        }
        logger.warn('collection_cycle_failed', {
          environmentId,
          state: state.state,
          errorName: error.name,
          errorCode: state.lastErrorCode
        });
        return { state: state.state, pages, inserted };
      }
    }
  });

  function saveSuccessState(state, cursor, frontier, now) {
    const lagSeconds = cursor.initialized && frontier && compareCursors(cursor, frontier) < 0
      ? Math.max(0, Math.floor(
          (new Date(frontier.createdAt).getTime() - new Date(cursor.createdAt).getTime()) / 1000
        ))
      : 0;
    const value = createState({
      sourceId: environmentId,
      state,
      now,
      previous: stateRepository.get(environmentId),
      lagSeconds,
      detail: state === MONITOR_STATES.CURRENT
        ? 'The durable cursor is current with the available source.'
        : 'The collector reached its bounded page limit and will continue safely.'
    });
    stateRepository.save(value);
    return value;
  }
}
