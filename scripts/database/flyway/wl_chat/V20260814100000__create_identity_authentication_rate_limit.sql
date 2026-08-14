CREATE TABLE [identity].[authentication_rate_limit] (
    scope_type VARCHAR(16) NOT NULL,
    scope_hash BINARY(32) NOT NULL,
    window_started_at DATETIME2(7) NOT NULL,
    window_expires_at DATETIME2(7) NOT NULL,
    attempt_count INT NOT NULL,
    updated_at DATETIME2(7) NOT NULL,

    CONSTRAINT pk_identity_authentication_rate_limit
        PRIMARY KEY CLUSTERED (scope_type, scope_hash),

    CONSTRAINT ck_identity_authentication_rate_limit_scope
        CHECK (scope_type IN ('ACCOUNT', 'SOURCE')),

    CONSTRAINT ck_identity_authentication_rate_limit_count
        CHECK (attempt_count > 0),

    CONSTRAINT ck_identity_authentication_rate_limit_window
        CHECK (window_expires_at > window_started_at
            AND updated_at >= window_started_at)
);

CREATE INDEX ix_identity_authentication_rate_limit_expiry
    ON [identity].[authentication_rate_limit] (window_expires_at);

GRANT SELECT, INSERT, UPDATE, DELETE
    ON [identity].[authentication_rate_limit] TO [${app_login}];

IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'messenger_migrator'
)
BEGIN
    GRANT SELECT ON [identity].[authentication_rate_limit] TO [messenger_migrator];
END;
