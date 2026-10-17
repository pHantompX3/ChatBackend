ALTER TABLE projected_event RENAME TO projected_event_legacy;

CREATE TABLE projected_event (
    environment_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
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
    inserted_at TEXT NOT NULL,
    PRIMARY KEY (environment_id, event_id)
);

INSERT INTO projected_event (
    environment_id, event_id, schema_version, source_type, source_instance_id, event_name,
    occurred_at, received_at, severity, dimensions_json, measurements_json,
    source_created_at, source_event_id, inserted_at
)
SELECT
    'fixture', event_id, schema_version, source_type, source_instance_id, event_name,
    occurred_at, received_at, severity, dimensions_json, measurements_json,
    source_created_at, source_event_id, inserted_at
FROM projected_event_legacy;

DROP TABLE projected_event_legacy;

CREATE INDEX ix_projected_event_environment_occurred_at
    ON projected_event (environment_id, occurred_at, event_id);

ALTER TABLE metric_bucket RENAME TO metric_bucket_legacy;

CREATE TABLE metric_bucket (
    environment_id TEXT NOT NULL,
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
    PRIMARY KEY (
        environment_id, resolution, bucket_start, event_name, method, route, status_class
    )
);

INSERT INTO metric_bucket (
    environment_id, resolution, bucket_start, event_name, method, route, status_class,
    request_count, failure_count, duration_count, duration_sum, duration_min, duration_max
)
SELECT
    'fixture', resolution, bucket_start, event_name, method, route, status_class,
    request_count, failure_count, duration_count, duration_sum, duration_min, duration_max
FROM metric_bucket_legacy;

DROP TABLE metric_bucket_legacy;

CREATE INDEX ix_metric_bucket_environment_time
    ON metric_bucket (environment_id, resolution, bucket_start);
