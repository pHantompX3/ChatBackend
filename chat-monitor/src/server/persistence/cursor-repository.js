export const INITIAL_CURSOR = Object.freeze({
  createdAt: '0001-01-01T00:00:00.000Z',
  eventId: '00000000-0000-0000-0000-000000000000',
  initialized: false
});

export function createCursorRepository(database) {
  const read = database.prepare(
    'SELECT created_at AS createdAt, event_id AS eventId FROM collector_cursor WHERE source_id = ?'
  );
  const write = database.prepare(`
    INSERT INTO collector_cursor(source_id, created_at, event_id, updated_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(source_id) DO UPDATE SET
      created_at = excluded.created_at,
      event_id = excluded.event_id,
      updated_at = excluded.updated_at
  `);

  return Object.freeze({
    get(sourceId) {
      const cursor = read.get(sourceId);
      return cursor ? { ...cursor, initialized: true } : { ...INITIAL_CURSOR };
    },
    save(sourceId, cursor, now = new Date()) {
      write.run(sourceId, cursor.createdAt, cursor.eventId, now.toISOString());
    }
  });
}
