IF SCHEMA_ID(N'audit') IS NULL
    EXEC(N'CREATE SCHEMA audit AUTHORIZATION dbo');

CREATE TABLE audit.http_audit_event (
    event_id UNIQUEIDENTIFIER NOT NULL,
    schema_version NVARCHAR(16) NOT NULL,
    event_type NVARCHAR(120) NOT NULL,
    occurred_at DATETIME2(7) NOT NULL,

    request_id NVARCHAR(80) NOT NULL,
    trace_id NVARCHAR(80) NULL,
    operation NVARCHAR(200) NOT NULL,
    method VARCHAR(12) NOT NULL,
    route_template NVARCHAR(300) NULL,
    path NVARCHAR(2048) NOT NULL,
    query NVARCHAR(2048) NULL,

    response_status INT NOT NULL,
    response_code NVARCHAR(120) NULL,
    duration_ms BIGINT NOT NULL,
    request_timestamp DATETIME2(7) NOT NULL,
    response_timestamp DATETIME2(7) NOT NULL,

    actor_user_id UNIQUEIDENTIFIER NULL,
    actor_username NVARCHAR(160) NULL,
    actor_auth_type NVARCHAR(40) NULL,

    target_type NVARCHAR(120) NULL,
    target_id NVARCHAR(120) NULL,

    source_ip VARCHAR(45) NULL,
    remote_ip VARCHAR(45) NULL,
    ip_resolution_source NVARCHAR(40) NULL,

    user_agent NVARCHAR(1024) NULL,
    device_type NVARCHAR(40) NULL,
    device_platform NVARCHAR(80) NULL,
    device_model NVARCHAR(120) NULL,
    os_family NVARCHAR(80) NULL,
    browser_family NVARCHAR(80) NULL,

    request_headers NVARCHAR(MAX) NULL,
    response_headers NVARCHAR(MAX) NULL,

    error_code NVARCHAR(120) NULL,
    error_message NVARCHAR(256) NULL,

    metadata NVARCHAR(MAX) NULL,
    record_hash VARBINARY(32) NOT NULL,

    created_at DATETIME2(7) NOT NULL
        CONSTRAINT df_http_audit_event_created_at DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_http_audit_event
        PRIMARY KEY NONCLUSTERED (event_id),

    CONSTRAINT ck_http_audit_event_schema_version
        CHECK (LEN(TRIM(schema_version)) > 0),

    CONSTRAINT ck_http_audit_event_event_type
        CHECK (LEN(TRIM(event_type)) > 0),

    CONSTRAINT ck_http_audit_event_method
        CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),

    CONSTRAINT ck_http_audit_event_response_status
        CHECK (response_status BETWEEN 100 AND 599),

    CONSTRAINT ck_http_audit_event_duration_ms
        CHECK (duration_ms >= 0),

    CONSTRAINT ck_http_audit_event_response_time
        CHECK (response_timestamp >= request_timestamp),

    CONSTRAINT ck_http_audit_event_request_headers_json
        CHECK (request_headers IS NULL OR ISJSON(request_headers) = 1),

    CONSTRAINT ck_http_audit_event_response_headers_json
        CHECK (response_headers IS NULL OR ISJSON(response_headers) = 1),

    CONSTRAINT ck_http_audit_event_metadata_json
        CHECK (metadata IS NULL OR ISJSON(metadata) = 1)
);

EXEC(
    N'ALTER TABLE audit.http_audit_event '
        + N'ADD CONSTRAINT fk_http_audit_event_actor_user '
        + N'FOREIGN KEY (actor_user_id) REFERENCES [identity].[user_account](id)'
);

CREATE UNIQUE CLUSTERED INDEX cix_http_audit_event_occurred_at
    ON audit.http_audit_event (occurred_at, event_id);

CREATE INDEX ix_http_audit_event_request_id
    ON audit.http_audit_event (request_id);

CREATE INDEX ix_http_audit_event_trace_id
    ON audit.http_audit_event (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE INDEX ix_http_audit_event_actor_user
    ON audit.http_audit_event (actor_user_id, occurred_at)
    WHERE actor_user_id IS NOT NULL;

CREATE INDEX ix_http_audit_event_event_type
    ON audit.http_audit_event (event_type, occurred_at);

CREATE INDEX ix_http_audit_event_source_ip
    ON audit.http_audit_event (source_ip, occurred_at)
    WHERE source_ip IS NOT NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'${app_login}'
)
BEGIN
    EXEC(N'CREATE USER [${app_login}] FOR LOGIN [${app_login}]');
END;

GRANT INSERT ON audit.http_audit_event TO [${app_login}];
GRANT SELECT ON audit.http_audit_event TO [${app_login}];
DENY UPDATE ON audit.http_audit_event TO [${app_login}];
DENY DELETE ON audit.http_audit_event TO [${app_login}];

IF SUSER_ID(N'messenger_migrator') IS NOT NULL
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.database_principals
        WHERE name = N'messenger_migrator'
    )
    BEGIN
        CREATE USER [messenger_migrator] FOR LOGIN [messenger_migrator];
    END;

    GRANT SELECT ON audit.http_audit_event TO [messenger_migrator];
END;

DENY ALTER ON SCHEMA::audit TO [${app_login}];
DENY CONTROL ON SCHEMA::audit TO [${app_login}];
