function bucketStart(occurredAt, resolution) {
  const date = new Date(occurredAt);
  if (resolution === 'day') {
    date.setUTCHours(0, 0, 0, 0);
  } else {
    date.setUTCMinutes(0, 0, 0);
  }
  return date.toISOString();
}

export function createAggregator(database) {
  const upsert = database.prepare(`
    INSERT INTO metric_bucket(
      environment_id, resolution, bucket_start, event_name, method, route, status_class,
      request_count, failure_count, duration_count, duration_sum, duration_min, duration_max
    ) VALUES (
      @environmentId, @resolution, @bucketStart, @eventName, @method, @route, @statusClass,
      1, @failureCount, @durationCount, @durationSum, @durationMin, @durationMax
    ) ON CONFLICT(environment_id, resolution, bucket_start, event_name, method, route, status_class)
    DO UPDATE SET
      request_count = request_count + 1,
      failure_count = failure_count + excluded.failure_count,
      duration_count = duration_count + excluded.duration_count,
      duration_sum = duration_sum + excluded.duration_sum,
      duration_min = CASE
        WHEN excluded.duration_min IS NULL THEN duration_min
        WHEN duration_min IS NULL THEN excluded.duration_min
        ELSE MIN(duration_min, excluded.duration_min)
      END,
      duration_max = CASE
        WHEN excluded.duration_max IS NULL THEN duration_max
        WHEN duration_max IS NULL THEN excluded.duration_max
        ELSE MAX(duration_max, excluded.duration_max)
      END
  `);

  return Object.freeze({
    record(environmentId, event) {
      const duration = event.measurements.durationMs;
      const values = {
        environmentId,
        eventName: event.eventName,
        method: event.dimensions.method ?? 'unknown',
        route: event.dimensions.route ?? 'unknown',
        statusClass: event.dimensions.statusClass ?? 'unknown',
        failureCount: event.dimensions.statusClass === '5xx' ? 1 : 0,
        durationCount: duration === undefined ? 0 : 1,
        durationSum: duration ?? 0,
        durationMin: duration ?? null,
        durationMax: duration ?? null
      };

      for (const resolution of ['hour', 'day']) {
        upsert.run({ ...values, resolution, bucketStart: bucketStart(event.occurredAt, resolution) });
      }
    }
  });
}
