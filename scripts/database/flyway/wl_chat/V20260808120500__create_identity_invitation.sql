CREATE TABLE [identity].[invitation] (
    id UNIQUEIDENTIFIER NOT NULL,
    token_hash VARBINARY(64) NOT NULL,
    created_by UNIQUEIDENTIFIER NOT NULL,
    expires_at DATETIME2(7) NOT NULL,
    redeemed_at DATETIME2(7) NULL,
    redeemed_by UNIQUEIDENTIFIER NULL,
    revoked_at DATETIME2(7) NULL,
    created_at DATETIME2(7) NOT NULL
        CONSTRAINT df_invitation_created_at DEFAULT SYSUTCDATETIME(),

    CONSTRAINT pk_invitation PRIMARY KEY NONCLUSTERED (id),
    CONSTRAINT uq_invitation_token_hash UNIQUE (token_hash),

    CONSTRAINT fk_invitation_created_by
        FOREIGN KEY (created_by)
        REFERENCES [identity].[user_account](id),
    CONSTRAINT fk_invitation_redeemed_by
        FOREIGN KEY (redeemed_by)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT ck_invitation_expiry_after_create
        CHECK (expires_at > created_at),
    CONSTRAINT ck_invitation_redeemed_columns_in_sync
        CHECK (
            (redeemed_at IS NULL AND redeemed_by IS NULL)
            OR (redeemed_at IS NOT NULL AND redeemed_by IS NOT NULL)
        ),
    CONSTRAINT ck_invitation_not_redeemed_and_revoked
        CHECK (NOT (redeemed_at IS NOT NULL AND revoked_at IS NOT NULL))
);

CREATE UNIQUE CLUSTERED INDEX cix_invitation_created_at
    ON [identity].[invitation] (created_at, id);

CREATE INDEX ix_invitation_expires_at
    ON [identity].[invitation] (expires_at);

CREATE INDEX ix_invitation_redeemed_at
    ON [identity].[invitation] (redeemed_at)
    WHERE redeemed_at IS NOT NULL;
