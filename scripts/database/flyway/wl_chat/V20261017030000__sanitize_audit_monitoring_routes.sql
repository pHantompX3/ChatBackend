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
    CASE
        WHEN operation = N'unknown.operation' THEN N'unmatched'
        ELSE route_template
    END AS route_template,
    response_status,
    response_code,
    duration_ms,
    error_code
FROM [audit].[http_audit_event];
GO

GRANT SELECT ON OBJECT::[audit].[monitoring_http_event] TO [audit_monitor];
GO
