IF SCHEMA_ID(N'audit') IS NULL
    THROW 50001, 'The audit schema must exist before creating the monitoring projection.', 1;

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER VIEW [audit].[monitoring_http_event]
WITH SCHEMABINDING
AS
SELECT
    event_id,
    created_at,
    occurred_at,
    operation,
    method,
    route_template,
    response_status,
    response_code,
    duration_ms,
    error_code
FROM [audit].[http_audit_event];
GO

IF DATABASE_PRINCIPAL_ID(N'audit_monitor') IS NULL
BEGIN
    CREATE ROLE [audit_monitor] AUTHORIZATION [dbo];
END;
GO

GRANT SELECT ON OBJECT::[audit].[monitoring_http_event] TO [audit_monitor];

DENY SELECT, INSERT, UPDATE, DELETE
    ON OBJECT::[audit].[http_audit_event] TO [audit_monitor];
DENY SELECT ON SCHEMA::[identity] TO [audit_monitor];
DENY SELECT ON SCHEMA::[messaging] TO [audit_monitor];
DENY SELECT ON SCHEMA::[platform] TO [audit_monitor];
DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, CONTROL TO [audit_monitor];
GO
