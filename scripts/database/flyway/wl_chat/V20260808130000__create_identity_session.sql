IF SCHEMA_ID(N'identity') IS NULL
    EXEC(N'CREATE SCHEMA identity AUTHORIZATION dbo');

CREATE TABLE [identity].[session] (
    id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    token_hash VARBINARY(64) NOT NULL,
    created_at DATETIME2(7) NOT NULL,
    expires_at DATETIME2(7) NOT NULL,
    last_seen_at DATETIME2(7) NULL,
    revoked_at DATETIME2(7) NULL,
    user_agent NVARCHAR(1024) NULL,
    source_address VARCHAR(45) NULL,
    status NVARCHAR(20) NOT NULL,

    CONSTRAINT pk_identity_session
        PRIMARY KEY NONCLUSTERED (id),

    CONSTRAINT fk_identity_session_user
        FOREIGN KEY (user_id)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT ck_identity_session_status
        CHECK (status IN (N'ACTIVE', N'REVOKED', N'EXPIRED')),

    CONSTRAINT ck_identity_session_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT ck_identity_session_revoke_semantics
        CHECK (revoked_at IS NULL OR status = N'REVOKED')
);

CREATE UNIQUE INDEX ux_identity_session_token_hash
    ON [identity].[session] (token_hash);

CREATE INDEX ix_identity_session_user_active
    ON [identity].[session] (user_id, status, expires_at);

CREATE INDEX ix_identity_session_expires_at
    ON [identity].[session] (expires_at);

IF EXISTS (
    SELECT 1
    FROM sys.server_principals
    WHERE name = N'messenger_migrator'
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.database_principals
        WHERE name = N'messenger_migrator'
    )
    BEGIN
        CREATE USER [messenger_migrator] FOR LOGIN [messenger_migrator];
    END;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'${app_login}'
)
BEGIN
    EXEC(N'CREATE USER [${app_login}] FOR LOGIN [${app_login}]');
END;

GRANT INSERT, SELECT ON [identity].[session] TO [${app_login}];
GRANT UPDATE, SELECT ON [identity].[session] TO [${app_login}];
DENY DELETE ON [identity].[session] TO [${app_login}];

IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'messenger_migrator'
)
BEGIN
    GRANT SELECT ON [identity].[session] TO [messenger_migrator];
END;
