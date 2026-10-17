const MAX_WINDOW_HOURS = 24 * 90;
const MAX_FAILURES = 100;

export function createMetricRepository(database) {
  const summary = database.prepare(`
    SELECT
      COALESCE(SUM(request_count), 0) AS requestCount,
      COALESCE(SUM(failure_count), 0) AS failureCount,
      COALESCE(SUM(CASE WHEN status_class = '4xx' THEN request_count ELSE 0 END), 0) AS expected4xxCount,
      COALESCE(SUM(duration_count), 0) AS durationCount,
      COALESCE(SUM(duration_sum), 0) AS durationSum,
      MAX(duration_max) AS durationMax
    FROM metric_bucket
    WHERE environment_id = ? AND resolution = 'hour' AND bucket_start >= ?
  `);
  const series = database.prepare(`
    SELECT bucket_start AS bucketStart,
      SUM(request_count) AS requestCount,
      SUM(failure_count) AS failureCount,
      SUM(CASE WHEN status_class = '4xx' THEN request_count ELSE 0 END) AS expected4xxCount,
      SUM(duration_count) AS durationCount,
      SUM(duration_sum) AS durationSum
    FROM metric_bucket
    WHERE environment_id = ? AND resolution = ? AND bucket_start >= ?
    GROUP BY bucket_start
    ORDER BY bucket_start
  `);
  const operations = database.prepare(`
    SELECT method, route,
      SUM(request_count) AS requestCount,
      SUM(failure_count) AS failureCount,
      SUM(CASE WHEN status_class = '4xx' THEN request_count ELSE 0 END) AS expected4xxCount,
      SUM(duration_count) AS durationCount,
      SUM(duration_sum) AS durationSum,
      MAX(duration_max) AS durationMax
    FROM metric_bucket
    WHERE environment_id = ? AND resolution = 'hour' AND bucket_start >= ?
    GROUP BY method, route
    ORDER BY requestCount DESC, method, route
    LIMIT ?
  `);
  const failures = database.prepare(`
    SELECT event_id AS eventId, occurred_at AS occurredAt, severity,
      event_name AS eventName, dimensions_json AS dimensionsJson,
      measurements_json AS measurementsJson
    FROM projected_event
    WHERE environment_id = ? AND occurred_at >= ?
      AND json_extract(dimensions_json, '$.statusClass') = '5xx'
    ORDER BY occurred_at DESC, event_id DESC
    LIMIT ?
  `);

  return Object.freeze({
    overview(environmentId, hours, now = new Date()) {
      const boundedHours = boundedInteger(hours, 1, MAX_WINDOW_HOURS, 24);
      const since = alignedSinceTimestamp(boundedHours, now, 'hour');
      return normalizeSummary(summary.get(environmentId, since), boundedHours, now);
    },
    timeSeries(environmentId, { hours, resolution }, now = new Date()) {
      const boundedHours = boundedInteger(hours, 1, MAX_WINDOW_HOURS, 24);
      const selectedResolution = resolution === 'day' ? 'day' : 'hour';
      return {
        hours: boundedHours,
        resolution: selectedResolution,
        generatedAt: now.toISOString(),
        points: series.all(
          environmentId,
          selectedResolution,
          alignedSinceTimestamp(boundedHours, now, selectedResolution)
        )
          .map(normalizeMetricRow)
      };
    },
    operations(environmentId, { hours, limit }, now = new Date()) {
      const boundedHours = boundedInteger(hours, 1, MAX_WINDOW_HOURS, 24);
      const boundedLimit = boundedInteger(limit, 1, 50, 10);
      return {
        hours: boundedHours,
        generatedAt: now.toISOString(),
        operations: operations.all(
          environmentId,
          alignedSinceTimestamp(boundedHours, now, 'hour'),
          boundedLimit
        ).map(normalizeMetricRow)
      };
    },
    failures(environmentId, { hours, limit }, now = new Date()) {
      const boundedHours = boundedInteger(hours, 1, MAX_WINDOW_HOURS, 24);
      const boundedLimit = boundedInteger(limit, 1, MAX_FAILURES, 25);
      return {
        hours: boundedHours,
        generatedAt: now.toISOString(),
        failures: failures.all(
          environmentId,
          sinceTimestamp(boundedHours, now),
          boundedLimit
        ).map((row) => ({
          eventId: row.eventId,
          occurredAt: row.occurredAt,
          severity: row.severity,
          eventName: row.eventName,
          dimensions: JSON.parse(row.dimensionsJson),
          measurements: JSON.parse(row.measurementsJson)
        }))
      };
    }
  });
}

function normalizeSummary(row, hours, now) {
  const requestCount = Number(row.requestCount);
  const failureCount = Number(row.failureCount);
  const durationCount = Number(row.durationCount);
  return {
    hours,
    generatedAt: now.toISOString(),
    requestCount,
    failureCount,
    expected4xxCount: Number(row.expected4xxCount),
    failureRate: requestCount === 0 ? 0 : failureCount / requestCount,
    averageDurationMs: durationCount === 0 ? null : Number(row.durationSum) / durationCount,
    maximumDurationMs: row.durationMax === null ? null : Number(row.durationMax)
  };
}

function normalizeMetricRow(row) {
  const durationCount = Number(row.durationCount);
  return {
    ...row,
    requestCount: Number(row.requestCount),
    failureCount: Number(row.failureCount),
    expected4xxCount: Number(row.expected4xxCount),
    averageDurationMs: durationCount === 0 ? null : Number(row.durationSum) / durationCount,
    durationCount: undefined,
    durationSum: undefined,
    durationMax: row.durationMax === null || row.durationMax === undefined
      ? undefined
      : Number(row.durationMax)
  };
}

function sinceTimestamp(hours, now) {
  return new Date(now.getTime() - hours * 60 * 60 * 1000).toISOString();
}

function alignedSinceTimestamp(hours, now, resolution) {
  const since = new Date(now.getTime() - hours * 60 * 60 * 1000);
  if (resolution === 'day') {
    since.setUTCHours(0, 0, 0, 0);
  } else {
    since.setUTCMinutes(0, 0, 0);
  }
  return since.toISOString();
}

export function boundedInteger(value, minimum, maximum, fallback) {
  if (value === undefined) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new RangeError(`value must be an integer between ${minimum} and ${maximum}`);
  }
  return parsed;
}
