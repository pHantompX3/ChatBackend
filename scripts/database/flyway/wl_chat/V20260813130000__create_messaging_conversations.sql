CREATE TABLE [messaging].[conversation] (
    id UNIQUEIDENTIFIER NOT NULL,
    conversation_type VARCHAR(20) NOT NULL,
    title NVARCHAR(200) NULL,
    created_by UNIQUEIDENTIFIER NOT NULL,
    next_message_sequence BIGINT NOT NULL
        CONSTRAINT df_messaging_conversation_next_sequence DEFAULT (1),
    created_at DATETIME2(7) NOT NULL,
    updated_at DATETIME2(7) NOT NULL,

    CONSTRAINT pk_messaging_conversation
        PRIMARY KEY NONCLUSTERED (id),

    CONSTRAINT fk_messaging_conversation_created_by
        FOREIGN KEY (created_by)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT ck_messaging_conversation_type
        CHECK (conversation_type IN ('DIRECT', 'GROUP')),

    CONSTRAINT ck_messaging_conversation_title
        CHECK (
            (conversation_type = 'DIRECT' AND title IS NULL)
            OR
            (conversation_type = 'GROUP' AND title IS NOT NULL AND LEN(LTRIM(RTRIM(title))) > 0)
        ),

    CONSTRAINT ck_messaging_conversation_next_sequence
        CHECK (next_message_sequence > 0),

    CONSTRAINT ck_messaging_conversation_timestamps
        CHECK (updated_at >= created_at)
);

CREATE TABLE [messaging].[conversation_member] (
    conversation_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    conversation_role VARCHAR(20) NOT NULL,
    joined_at DATETIME2(7) NOT NULL,
    left_at DATETIME2(7) NULL,
    last_delivered_sequence BIGINT NOT NULL
        CONSTRAINT df_messaging_member_last_delivered DEFAULT (0),
    last_read_sequence BIGINT NOT NULL
        CONSTRAINT df_messaging_member_last_read DEFAULT (0),

    CONSTRAINT pk_messaging_conversation_member
        PRIMARY KEY NONCLUSTERED (conversation_id, user_id),

    CONSTRAINT fk_messaging_conversation_member_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES [messaging].[conversation](id),

    CONSTRAINT fk_messaging_conversation_member_user
        FOREIGN KEY (user_id)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT ck_messaging_conversation_member_role
        CHECK (conversation_role IN ('OWNER', 'ADMIN', 'MEMBER')),

    CONSTRAINT ck_messaging_conversation_member_left_at
        CHECK (left_at IS NULL OR left_at >= joined_at),

    CONSTRAINT ck_messaging_conversation_member_positions
        CHECK (
            last_delivered_sequence >= 0
            AND last_read_sequence >= 0
            AND last_read_sequence <= last_delivered_sequence
        )
);

CREATE TABLE [messaging].[direct_conversation_pair] (
    conversation_id UNIQUEIDENTIFIER NOT NULL,
    participant_low_id UNIQUEIDENTIFIER NOT NULL,
    participant_high_id UNIQUEIDENTIFIER NOT NULL,

    CONSTRAINT pk_messaging_direct_conversation_pair
        PRIMARY KEY NONCLUSTERED (conversation_id),

    CONSTRAINT fk_messaging_direct_pair_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES [messaging].[conversation](id),

    CONSTRAINT fk_messaging_direct_pair_low_user
        FOREIGN KEY (participant_low_id)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT fk_messaging_direct_pair_high_user
        FOREIGN KEY (participant_high_id)
        REFERENCES [identity].[user_account](id),

    CONSTRAINT uq_messaging_direct_pair
        UNIQUE (participant_low_id, participant_high_id),

    CONSTRAINT ck_messaging_direct_pair_canonical_order
        CHECK (
            CONVERT(CHAR(36), participant_low_id) COLLATE Latin1_General_100_BIN2
            < CONVERT(CHAR(36), participant_high_id) COLLATE Latin1_General_100_BIN2
        )
);

CREATE INDEX ix_messaging_member_user_active
    ON [messaging].[conversation_member] (user_id, left_at, conversation_id);

CREATE INDEX ix_messaging_member_conversation_active
    ON [messaging].[conversation_member] (conversation_id, left_at, joined_at);

CREATE UNIQUE INDEX ux_messaging_member_active_owner
    ON [messaging].[conversation_member] (conversation_id)
    WHERE left_at IS NULL AND conversation_role = 'OWNER';

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'${app_login}'
)
BEGIN
    EXEC(N'CREATE USER [${app_login}] FOR LOGIN [${app_login}]');
END;

GRANT SELECT, INSERT, UPDATE ON [messaging].[conversation] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE ON [messaging].[conversation_member] TO [${app_login}];
GRANT SELECT, INSERT ON [messaging].[direct_conversation_pair] TO [${app_login}];

DENY DELETE ON [messaging].[conversation] TO [${app_login}];
DENY DELETE ON [messaging].[conversation_member] TO [${app_login}];
DENY DELETE ON [messaging].[direct_conversation_pair] TO [${app_login}];

IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'messenger_migrator'
)
BEGIN
    GRANT SELECT ON [messaging].[conversation] TO [messenger_migrator];
    GRANT SELECT ON [messaging].[conversation_member] TO [messenger_migrator];
    GRANT SELECT ON [messaging].[direct_conversation_pair] TO [messenger_migrator];
END;
