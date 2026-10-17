export function compareCursors(left, right) {
  const timestampComparison = left.createdAt.localeCompare(right.createdAt);
  return timestampComparison === 0 ? left.eventId.localeCompare(right.eventId) : timestampComparison;
}

export function lastCursor(records, fallback) {
  return records.length === 0 ? fallback : records.at(-1).cursor;
}
