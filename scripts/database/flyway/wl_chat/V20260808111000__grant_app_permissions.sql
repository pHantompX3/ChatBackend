/*
  Grants least-privilege DML permissions for the application user.
  Flyway placeholder used:
    ${app_login}
*/
IF NOT EXISTS (
    SELECT 1
    FROM sys.database_role_members drm
    JOIN sys.database_principals rolep ON drm.role_principal_id = rolep.principal_id
    JOIN sys.database_principals memberp ON drm.member_principal_id = memberp.principal_id
    WHERE rolep.name = 'db_datareader'
      AND memberp.name = '${app_login}'
)
BEGIN
    EXEC(N'ALTER ROLE [db_datareader] ADD MEMBER [${app_login}]');
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_role_members drm
    JOIN sys.database_principals rolep ON drm.role_principal_id = rolep.principal_id
    JOIN sys.database_principals memberp ON drm.member_principal_id = memberp.principal_id
    WHERE rolep.name = 'db_datawriter'
      AND memberp.name = '${app_login}'
)
BEGIN
    EXEC(N'ALTER ROLE [db_datawriter] ADD MEMBER [${app_login}]');
END;

GRANT EXECUTE TO [${app_login}];
GRANT VIEW DEFINITION TO [${app_login}];
