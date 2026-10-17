export function createEventRepository(database) {
  const insert = database.prepare(`
    INSERT OR IGNORE INTO projected_event (
      environment_id, event_id, schema_version, source_type, source_instance_id, event_name,
      occurred_at, received_at, severity, dimensions_json, measurements_json,
      source_created_at, source_event_id, inserted_at
    ) VALUES (
      @environmentId, @eventId, @schemaVersion, @sourceType, @sourceInstanceId, @eventName,
      @occurredAt, @receivedAt, @severity, @dimensionsJson, @measurementsJson,
      @sourceCreatedAt, @sourceEventId, @insertedAt
    )
  `);

  return Object.freeze({
    insert(environmentId, record, now = new Date()) {
      const result = insert.run({
        environmentId,
        ...record.event,
        dimensionsJson: JSON.stringify(record.event.dimensions),
        measurementsJson: JSON.stringify(record.event.measurements),
        sourceCreatedAt: record.cursor.createdAt,
        sourceEventId: record.cursor.eventId,
        insertedAt: now.toISOString()
      });
      return result.changes === 1;
    },
    count(environmentId) {
      return database.prepare(
        'SELECT COUNT(*) AS count FROM projected_event WHERE environment_id = ?'
      ).get(environmentId).count;
    },
    retainedRange(environmentId) {
      return database.prepare(
        `SELECT MIN(occurred_at) AS oldest, MAX(occurred_at) AS newest
         FROM projected_event WHERE environment_id = ?`
      ).get(environmentId);
    }
  });
}
