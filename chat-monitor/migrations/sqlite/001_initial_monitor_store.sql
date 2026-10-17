CREATE TABLE projected_event (
    event_id TEXT PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    source_instance_id TEXT NOT NULL,
    event_name TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    severity TEXT NOT NULL,
    dimensions_json TEXT NOT NULL,
    measurements_json TEXT NOT NULL,
    source_created_at TEXT NOT NULL,
    source_event_id TEXT NOT NULL,
    inserted_at TEXT NOT NULL
);

CREATE INDEX ix_projected_event_occurred_at
    ON projected_event (occurred_at, event_id);

CREATE TABLE collector_cursor (
    source_id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    event_id TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE collector_state (
    source_id TEXT PRIMARY KEY,
    state TEXT NOT NULL,
    last_attempt_at TEXT,
    last_success_at TEXT,
    last_error_code TEXT,
    lag_seconds INTEGER,
    detail TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE history_gap (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id TEXT NOT NULL,
    expected_created_at TEXT NOT NULL,
    expected_event_id TEXT NOT NULL,
    available_created_at TEXT NOT NULL,
    available_event_id TEXT NOT NULL,
    detected_at TEXT NOT NULL
);

CREATE TABLE metric_bucket (
    resolution TEXT NOT NULL,
    bucket_start TEXT NOT NULL,
    event_name TEXT NOT NULL,
    method TEXT NOT NULL,
    route TEXT NOT NULL,
    status_class TEXT NOT NULL,
    request_count INTEGER NOT NULL,
    failure_count INTEGER NOT NULL,
    duration_count INTEGER NOT NULL,
    duration_sum REAL NOT NULL,
    duration_min REAL,
    duration_max REAL,
    PRIMARY KEY (resolution, bucket_start, event_name, method, route, status_class)
);

CREATE INDEX ix_metric_bucket_time
    ON metric_bucket (resolution, bucket_start);
