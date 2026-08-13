CREATE TABLE [messaging].[message] (
    id UNIQUEIDENTIFIER NOT NULL,
    conversation_id UNIQUEIDENTIFIER NOT NULL,
    sender_id UNIQUEIDENTIFIER NOT NULL,
    client_message_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    body NVARCHAR(4000) NULL,
    created_at DATETIME2(7) NOT NULL,
    edited_at DATETIME2(7) NULL,
    deleted_at DATETIME2(7) NULL,

    CONSTRAINT pk_messaging_message
        PRIMARY KEY NONCLUSTERED (id),

    CONSTRAINT fk_messaging_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES [messaging].[conversation](id),

    CONSTRAINT fk_messaging_message_sender_membership
        FOREIGN KEY (conversation_id, sender_id)
        REFERENCES [messaging].[conversation_member](conversation_id, user_id),

    CONSTRAINT uq_messaging_message_client_id
        UNIQUE NONCLUSTERED (sender_id, client_message_id),

    CONSTRAINT uq_messaging_message_sequence
        UNIQUE CLUSTERED (conversation_id, sequence_number),

    CONSTRAINT ck_messaging_message_sequence_positive
        CHECK (sequence_number > 0),

    CONSTRAINT ck_messaging_message_type
        CHECK (message_type IN ('TEXT', 'SYSTEM')),

    CONSTRAINT ck_messaging_message_body
        CHECK (
            (deleted_at IS NOT NULL AND body IS NULL)
            OR (
                deleted_at IS NULL
                AND body IS NOT NULL
                AND LEN(TRIM(body)) BETWEEN 1 AND 4000
            )
        ),

    CONSTRAINT ck_messaging_message_timestamps
        CHECK (
            (edited_at IS NULL OR edited_at >= created_at)
            AND (deleted_at IS NULL OR deleted_at >= COALESCE(edited_at, created_at))
        )
);

GRANT SELECT, INSERT, UPDATE ON [messaging].[message] TO [${app_login}];
DENY DELETE ON [messaging].[message] TO [${app_login}];

IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'messenger_migrator'
)
BEGIN
    GRANT SELECT ON [messaging].[message] TO [messenger_migrator];
END;
