/*
  Creates the application SQL login and database user in wl_chat.
  Flyway placeholders used:
    ${app_login}
    ${app_password}
*/
IF NOT EXISTS (
    SELECT 1
    FROM sys.server_principals
    WHERE name = '${app_login}'
)
BEGIN
    DECLARE @create_login_sql NVARCHAR(4000);
    DECLARE @safe_password NVARCHAR(512);

    SET @safe_password = REPLACE('${app_password}', '''', '''''');
    SET @create_login_sql =
        N'CREATE LOGIN [' + '${app_login}' + N'] WITH PASSWORD = N''' + @safe_password + N''', CHECK_POLICY = OFF, CHECK_EXPIRATION = OFF;';

    EXEC(@create_login_sql);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_principals
    WHERE name = '${app_login}'
)
BEGIN
    DECLARE @create_user_sql NVARCHAR(4000);

    SET @create_user_sql =
        N'CREATE USER [' + '${app_login}' + N'] FOR LOGIN [' + '${app_login}' + N'] WITH DEFAULT_SCHEMA = [dbo];';

    EXEC(@create_user_sql);
END;
