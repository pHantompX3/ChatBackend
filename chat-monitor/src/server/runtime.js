import { createAggregator } from './aggregation/aggregator.js';
import { createRetentionService } from './aggregation/retention.js';
import { createCollector } from './collector/collector.js';
import { createScheduler } from './collector/scheduler.js';
import { createFixtureSource } from './fixtures/fixture-source.js';
import { createCursorRepository } from './persistence/cursor-repository.js';
import { createEventRepository } from './persistence/event-repository.js';
import { openMonitorDatabase } from './persistence/sqlite.js';
import { createStateRepository } from './persistence/state-repository.js';
import { createMetricRepository } from './persistence/metric-repository.js';
import { createAlertRepository } from './persistence/alert-repository.js';
import { createAlertService } from './alerting/alert-service.js';
import { createSqlServerSource } from './production/sql-server-client.js';

export function createMonitorRuntime(config, logger) {
  const database = openMonitorDatabase(config.sqlitePath);
  const eventRepository = createEventRepository(database);
  const cursorRepository = createCursorRepository(database);
  const stateRepository = createStateRepository(database);
  const metricRepository = createMetricRepository(database);
  const alertRepository = createAlertRepository(database);
  const alertService = createAlertService({
    alertRepository,
    metricRepository,
    stateRepository,
    logger
  });
  const aggregator = createAggregator(database);
  const retention = createRetentionService(database, config);
  for (const profile of config.environments.filter(({ mode }) => mode === 'planned')) {
    const now = new Date().toISOString();
    stateRepository.save({
      sourceId: profile.id,
      state: 'SOURCE_UNAVAILABLE',
      lastAttemptAt: null,
      lastSuccessfulCollectionAt: null,
      lastErrorCode: 'ENVIRONMENT_NOT_DEPLOYED',
      lagSeconds: null,
      detail: profile.unavailableReason,
      updatedAt: now
    });
    alertService.evaluate(profile.id);
  }
  const environments = new Map(config.environments.map((profile) => {
    if (profile.mode === 'planned') {
      return [profile.id, Object.freeze({ profile, source: null, scheduler: null })];
    }
    const source = profile.mode === 'sql'
      ? createSqlServerSource(profile.source)
      : createFixtureSource(profile.fixturePath);
    const collector = createCollector({
      database,
      source,
      environmentId: profile.id,
      eventRepository,
      cursorRepository,
      stateRepository,
      aggregator,
      retention,
      pageSize: profile.pageSize,
      logger
    });
    const scheduler = createScheduler({
      collector,
      environmentId: profile.id,
      intervalSeconds: profile.pollIntervalSeconds,
      logger,
      afterCycle: () => alertService.evaluate(profile.id)
    });
    return [profile.id, Object.freeze({ profile, source, scheduler })];
  }));

  return Object.freeze({
    defaultEnvironmentId: config.defaultEnvironmentId,
    environments,
    stateRepository,
    eventRepository,
    metricRepository,
    alertService,
    async start() {
      await Promise.all([...environments.values()]
        .filter(({ scheduler }) => scheduler)
        .map(({ scheduler }) => scheduler.start()));
    },
    async close() {
      await Promise.all([...environments.values()]
        .filter(({ scheduler }) => scheduler)
        .map(({ scheduler }) => scheduler.stop()));
      await Promise.all([...environments.values()]
        .filter(({ source }) => source)
        .map(({ source }) => source.close()));
      database.close();
    }
  });
}
