IF EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = N'${app_login}'
)
BEGIN
    REVOKE CONTROL ON SCHEMA::audit FROM [${app_login}];

    GRANT INSERT ON audit.http_audit_event TO [${app_login}];
    GRANT SELECT ON audit.http_audit_event TO [${app_login}];
    DENY UPDATE ON audit.http_audit_event TO [${app_login}];
    DENY DELETE ON audit.http_audit_event TO [${app_login}];
END;
