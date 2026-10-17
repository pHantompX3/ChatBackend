export function createAlertRepository(database) {
  const read = database.prepare(`
    SELECT environment_id AS environmentId, rule_id AS ruleId, state,
      pending_count AS pendingCount, recovery_count AS recoveryCount,
      first_observed_at AS firstObservedAt, last_observed_at AS lastObservedAt,
      acknowledged_at AS acknowledgedAt, suppressed_until AS suppressedUntil,
      last_delivery_status AS lastDeliveryStatus, last_delivery_error AS lastDeliveryError,
      detail
    FROM alert_state WHERE environment_id = ? AND rule_id = ?
  `);
  const list = database.prepare(`
    SELECT environment_id AS environmentId, rule_id AS ruleId, state,
      first_observed_at AS firstObservedAt, last_observed_at AS lastObservedAt,
      acknowledged_at AS acknowledgedAt, suppressed_until AS suppressedUntil,
      last_delivery_status AS lastDeliveryStatus, last_delivery_error AS lastDeliveryError,
      detail
    FROM alert_state WHERE environment_id = ?
    ORDER BY CASE state WHEN 'FIRING' THEN 0 WHEN 'PENDING' THEN 1
      WHEN 'ACKNOWLEDGED' THEN 2 ELSE 3 END, last_observed_at DESC
  `);
  const write = database.prepare(`
    INSERT INTO alert_state (
      environment_id, rule_id, state, pending_count, recovery_count,
      first_observed_at, last_observed_at, acknowledged_at, suppressed_until,
      last_delivery_status, last_delivery_error, detail
    ) VALUES (
      @environmentId, @ruleId, @state, @pendingCount, @recoveryCount,
      @firstObservedAt, @lastObservedAt, @acknowledgedAt, @suppressedUntil,
      @lastDeliveryStatus, @lastDeliveryError, @detail
    ) ON CONFLICT(environment_id, rule_id) DO UPDATE SET
      state = excluded.state, pending_count = excluded.pending_count,
      recovery_count = excluded.recovery_count, first_observed_at = excluded.first_observed_at,
      last_observed_at = excluded.last_observed_at, acknowledged_at = excluded.acknowledged_at,
      suppressed_until = excluded.suppressed_until,
      last_delivery_status = excluded.last_delivery_status,
      last_delivery_error = excluded.last_delivery_error, detail = excluded.detail
  `);
  const acknowledge = database.prepare(`
    UPDATE alert_state SET state = 'ACKNOWLEDGED', acknowledged_at = ?, last_observed_at = ?
    WHERE environment_id = ? AND rule_id = ? AND state = 'FIRING'
  `);
  const suppress = database.prepare(`
    UPDATE alert_state SET suppressed_until = ?, last_observed_at = ?
    WHERE environment_id = ? AND rule_id = ?
  `);
  const clear = database.prepare('DELETE FROM alert_state WHERE environment_id = ?');

  return Object.freeze({
    get(environmentId, ruleId) {
      return read.get(environmentId, ruleId);
    },
    list(environmentId) {
      return list.all(environmentId);
    },
    save(alert) {
      write.run({
        pendingCount: 0,
        recoveryCount: 0,
        firstObservedAt: null,
        acknowledgedAt: null,
        suppressedUntil: null,
        lastDeliveryStatus: 'NOT_ATTEMPTED',
        lastDeliveryError: null,
        ...alert
      });
    },
    acknowledge(environmentId, ruleId, now = new Date()) {
      return acknowledge.run(now.toISOString(), now.toISOString(), environmentId, ruleId).changes === 1;
    },
    suppress(environmentId, ruleId, suppressedUntil, now = new Date()) {
      return suppress.run(
        suppressedUntil.toISOString(), now.toISOString(), environmentId, ruleId
      ).changes === 1;
    },
    clear(environmentId) {
      return clear.run(environmentId).changes;
    }
  });
}
