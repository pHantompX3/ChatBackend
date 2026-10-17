const millisecondsPerDay = 24 * 60 * 60 * 1000;

export function createRetentionService(database, config) {
  const purgeEvents = database.prepare('DELETE FROM projected_event WHERE occurred_at < ?');
  const purgeBuckets = database.prepare('DELETE FROM metric_bucket WHERE bucket_start < ?');

  return Object.freeze({
    apply(now = new Date()) {
      const detailCutoff = new Date(now.getTime() - config.detailRetentionDays * millisecondsPerDay);
      const aggregateCutoff = new Date(now.getTime() - config.aggregateRetentionDays * millisecondsPerDay);
      return database.transaction(() => ({
        deletedEvents: purgeEvents.run(detailCutoff.toISOString()).changes,
        deletedBuckets: purgeBuckets.run(aggregateCutoff.toISOString()).changes
      }))();
    }
  });
}
