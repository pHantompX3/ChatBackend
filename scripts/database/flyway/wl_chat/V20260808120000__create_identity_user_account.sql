CREATE TABLE [identity].[user_account] (
    id UNIQUEIDENTIFIER NOT NULL,
    username NVARCHAR(160) NOT NULL,
    normalized_username NVARCHAR(160) NOT NULL,
    password_hash NVARCHAR(512) NOT NULL,
    system_role NVARCHAR(16) NOT NULL,
    status NVARCHAR(16) NOT NULL,
    created_at DATETIME2(7) NOT NULL
        CONSTRAINT df_user_account_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2(7) NOT NULL
        CONSTRAINT df_user_account_updated_at DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_user_account PRIMARY KEY NONCLUSTERED (id),
    CONSTRAINT uq_user_account_normalized_username UNIQUE (normalized_username),

    CONSTRAINT ck_user_account_username_not_blank
        CHECK (LEN(TRIM(username)) > 0),
    CONSTRAINT ck_user_account_normalized_username_not_blank
        CHECK (LEN(TRIM(normalized_username)) > 0),
    CONSTRAINT ck_user_account_normalized_username_canonical
        CHECK (normalized_username = LOWER(TRIM(username))),
    CONSTRAINT ck_user_account_system_role
        CHECK (system_role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_user_account_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE CLUSTERED INDEX cix_user_account_normalized_username
    ON [identity].[user_account] (normalized_username, id);
