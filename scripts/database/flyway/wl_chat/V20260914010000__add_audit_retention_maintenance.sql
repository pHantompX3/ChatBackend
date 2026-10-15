SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DATABASE_PRINCIPAL_ID(N'audit_maintenance') IS NULL
BEGIN
    CREATE ROLE [audit_maintenance] AUTHORIZATION [dbo];
END;
GO

CREATE OR ALTER PROCEDURE audit.purge_http_audit_events
    @retention_days int,
    @batch_size int = 1000,
    @max_batches int = 20
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET DEADLOCK_PRIORITY LOW;
    SET LOCK_TIMEOUT 1000;

    IF @retention_days < 62 OR @retention_days > 3650
        THROW 50020, 'retention_days must be between 62 and 3650.', 1;
    IF @batch_size < 1 OR @batch_size > 5000
        THROW 50021, 'batch_size must be between 1 and 5000.', 1;
    IF @max_batches < 1 OR @max_batches > 100
        THROW 50022, 'max_batches must be between 1 and 100.', 1;

    DECLARE @cutoff datetime2(7) = DATEADD(day, -@retention_days, SYSUTCDATETIME());
    DECLARE @deleted int = 1;
    DECLARE @total_deleted bigint = 0;
    DECLARE @batches int = 0;

    WHILE @deleted > 0 AND @batches < @max_batches
    BEGIN
        DELETE TOP (@batch_size)
        FROM audit.http_audit_event WITH (ROWLOCK, READPAST)
        WHERE occurred_at < @cutoff;

        SET @deleted = @@ROWCOUNT;
        SET @total_deleted += @deleted;
        SET @batches += 1;
    END;

    SELECT
        @cutoff AS cutoff_utc,
        @total_deleted AS rows_deleted,
        @batches AS batches_executed,
        MIN(occurred_at) AS oldest_remaining_utc
    FROM audit.http_audit_event;
END;
GO

GRANT EXECUTE ON audit.purge_http_audit_events TO [audit_maintenance];
DENY SELECT, INSERT, UPDATE, DELETE ON audit.http_audit_event TO [audit_maintenance];
GO

