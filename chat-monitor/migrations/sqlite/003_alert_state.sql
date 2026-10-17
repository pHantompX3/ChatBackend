CREATE TABLE alert_state (
    environment_id TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    state TEXT NOT NULL,
    pending_count INTEGER NOT NULL DEFAULT 0,
    recovery_count INTEGER NOT NULL DEFAULT 0,
    first_observed_at TEXT,
    last_observed_at TEXT NOT NULL,
    acknowledged_at TEXT,
    suppressed_until TEXT,
    last_delivery_status TEXT NOT NULL DEFAULT 'NOT_ATTEMPTED',
    last_delivery_error TEXT,
    detail TEXT NOT NULL,
    PRIMARY KEY (environment_id, rule_id)
);

CREATE INDEX ix_alert_state_environment_state
    ON alert_state (environment_id, state, last_observed_at);
