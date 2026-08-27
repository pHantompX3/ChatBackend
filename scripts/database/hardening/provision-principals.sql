SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

IF DB_ID(N'wl_chat') IS NULL
BEGIN
    CREATE DATABASE [wl_chat];
END;

DECLARE @runtime_login sysname = N'$(RUNTIME_LOGIN)';
DECLARE @migrator_login sysname = N'$(MIGRATOR_LOGIN)';
DECLARE @backup_login sysname = N'$(BACKUP_LOGIN)';
DECLARE @restore_login sysname = N'$(RESTORE_LOGIN)';

DECLARE @runtime_password_hex varchar(512) = '$(RUNTIME_PASSWORD_HEX)';
DECLARE @migrator_password_hex varchar(512) = '$(MIGRATOR_PASSWORD_HEX)';
DECLARE @backup_password_hex varchar(512) = '$(BACKUP_PASSWORD_HEX)';
DECLARE @restore_password_hex varchar(512) = '$(RESTORE_PASSWORD_HEX)';

DECLARE @runtime_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@runtime_password_hex"))',
        'varbinary(256)'));
DECLARE @migrator_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@migrator_password_hex"))',
        'varbinary(256)'));
DECLARE @backup_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@backup_password_hex"))',
        'varbinary(256)'));
DECLARE @restore_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@restore_password_hex"))',
        'varbinary(256)'));

IF @runtime_password IS NULL OR @migrator_password IS NULL
    OR @backup_password IS NULL OR @restore_password IS NULL
BEGIN
    THROW 50001, 'A hardened SQL principal password could not be decoded.', 1;
END;

DECLARE @login_sql nvarchar(max);

SET @login_sql = CASE WHEN SUSER_ID(@runtime_login) IS NULL THEN N'CREATE' ELSE N'ALTER' END
    + N' LOGIN ' + QUOTENAME(@runtime_login)
    + N' WITH PASSWORD = ' + QUOTENAME(@runtime_password, '''')
    + CASE WHEN SUSER_ID(@runtime_login) IS NULL
        THEN N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF' ELSE N'' END + N';';
EXEC sys.sp_executesql @login_sql;

SET @login_sql = CASE WHEN SUSER_ID(@migrator_login) IS NULL THEN N'CREATE' ELSE N'ALTER' END
    + N' LOGIN ' + QUOTENAME(@migrator_login)
    + N' WITH PASSWORD = ' + QUOTENAME(@migrator_password, '''')
    + CASE WHEN SUSER_ID(@migrator_login) IS NULL
        THEN N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF' ELSE N'' END + N';';
EXEC sys.sp_executesql @login_sql;

SET @login_sql = CASE WHEN SUSER_ID(@backup_login) IS NULL THEN N'CREATE' ELSE N'ALTER' END
    + N' LOGIN ' + QUOTENAME(@backup_login)
    + N' WITH PASSWORD = ' + QUOTENAME(@backup_password, '''')
    + CASE WHEN SUSER_ID(@backup_login) IS NULL
        THEN N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF' ELSE N'' END + N';';
EXEC sys.sp_executesql @login_sql;

SET @login_sql = CASE WHEN SUSER_ID(@restore_login) IS NULL THEN N'CREATE' ELSE N'ALTER' END
    + N' LOGIN ' + QUOTENAME(@restore_login)
    + N' WITH PASSWORD = ' + QUOTENAME(@restore_password, '''')
    + CASE WHEN SUSER_ID(@restore_login) IS NULL
        THEN N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF' ELSE N'' END + N';';
EXEC sys.sp_executesql @login_sql;

IF IS_SRVROLEMEMBER(N'dbcreator', @restore_login) <> 1
BEGIN
    SET @login_sql = N'ALTER SERVER ROLE [dbcreator] ADD MEMBER '
        + QUOTENAME(@restore_login) + N';';
    EXEC sys.sp_executesql @login_sql;
END;

SET @login_sql = N'GRANT VIEW DEFINITION ON LOGIN::' + QUOTENAME(@runtime_login)
    + N' TO ' + QUOTENAME(@migrator_login) + N';';
EXEC sys.sp_executesql @login_sql;

IF USER_ID(@backup_login) IS NULL
BEGIN
    SET @login_sql = N'CREATE USER ' + QUOTENAME(@backup_login)
        + N' FOR LOGIN ' + QUOTENAME(@backup_login) + N';';
    EXEC sys.sp_executesql @login_sql;
END;
IF USER_ID(@restore_login) IS NULL
BEGIN
    SET @login_sql = N'CREATE USER ' + QUOTENAME(@restore_login)
        + N' FOR LOGIN ' + QUOTENAME(@restore_login) + N';';
    EXEC sys.sp_executesql @login_sql;
END;
GO

USE [wl_chat];
GO

DECLARE @runtime_login sysname = N'$(RUNTIME_LOGIN)';
DECLARE @migrator_login sysname = N'$(MIGRATOR_LOGIN)';
DECLARE @backup_login sysname = N'$(BACKUP_LOGIN)';
DECLARE @preserve_runtime_fixed_roles bit =
    CASE WHEN LOWER(N'$(PRESERVE_RUNTIME_FIXED_ROLES)') = N'true' THEN 1 ELSE 0 END;
DECLARE @user_sql nvarchar(max);

IF USER_ID(@runtime_login) IS NULL
BEGIN
    SET @user_sql = N'CREATE USER ' + QUOTENAME(@runtime_login)
        + N' FOR LOGIN ' + QUOTENAME(@runtime_login) + N' WITH DEFAULT_SCHEMA = [dbo];';
    EXEC sys.sp_executesql @user_sql;
END;

IF USER_ID(@migrator_login) IS NULL
BEGIN
    SET @user_sql = N'CREATE USER ' + QUOTENAME(@migrator_login)
        + N' FOR LOGIN ' + QUOTENAME(@migrator_login) + N' WITH DEFAULT_SCHEMA = [dbo];';
    EXEC sys.sp_executesql @user_sql;
END;

IF USER_ID(@backup_login) IS NULL
BEGIN
    SET @user_sql = N'CREATE USER ' + QUOTENAME(@backup_login)
        + N' FOR LOGIN ' + QUOTENAME(@backup_login) + N' WITH DEFAULT_SCHEMA = [dbo];';
    EXEC sys.sp_executesql @user_sql;
END;

IF @preserve_runtime_fixed_roles = 1
BEGIN
    IF IS_ROLEMEMBER(N'db_datareader', @runtime_login) <> 1
    BEGIN
        SET @user_sql = N'ALTER ROLE [db_datareader] ADD MEMBER '
            + QUOTENAME(@runtime_login) + N';';
        EXEC sys.sp_executesql @user_sql;
    END;
    IF IS_ROLEMEMBER(N'db_datawriter', @runtime_login) <> 1
    BEGIN
        SET @user_sql = N'ALTER ROLE [db_datawriter] ADD MEMBER '
            + QUOTENAME(@runtime_login) + N';';
        EXEC sys.sp_executesql @user_sql;
    END;
END;
ELSE
BEGIN
    IF IS_ROLEMEMBER(N'db_datareader', @runtime_login) = 1
    BEGIN
        SET @user_sql = N'ALTER ROLE [db_datareader] DROP MEMBER '
            + QUOTENAME(@runtime_login) + N';';
        EXEC sys.sp_executesql @user_sql;
    END;
    IF IS_ROLEMEMBER(N'db_datawriter', @runtime_login) = 1
    BEGIN
        SET @user_sql = N'ALTER ROLE [db_datawriter] DROP MEMBER '
            + QUOTENAME(@runtime_login) + N';';
        EXEC sys.sp_executesql @user_sql;
    END;
END;

IF IS_ROLEMEMBER(N'db_ddladmin', @migrator_login) <> 1
BEGIN
    SET @user_sql = N'ALTER ROLE [db_ddladmin] ADD MEMBER '
        + QUOTENAME(@migrator_login) + N';';
    EXEC sys.sp_executesql @user_sql;
END;
IF IS_ROLEMEMBER(N'db_securityadmin', @migrator_login) <> 1
BEGIN
    SET @user_sql = N'ALTER ROLE [db_securityadmin] ADD MEMBER '
        + QUOTENAME(@migrator_login) + N';';
    EXEC sys.sp_executesql @user_sql;
END;
IF IS_ROLEMEMBER(N'db_datareader', @migrator_login) <> 1
BEGIN
    SET @user_sql = N'ALTER ROLE [db_datareader] ADD MEMBER '
        + QUOTENAME(@migrator_login) + N';';
    EXEC sys.sp_executesql @user_sql;
END;
IF IS_ROLEMEMBER(N'db_datawriter', @migrator_login) <> 1
BEGIN
    SET @user_sql = N'ALTER ROLE [db_datawriter] ADD MEMBER '
        + QUOTENAME(@migrator_login) + N';';
    EXEC sys.sp_executesql @user_sql;
END;

IF IS_ROLEMEMBER(N'db_backupoperator', @backup_login) <> 1
BEGIN
    SET @user_sql = N'ALTER ROLE [db_backupoperator] ADD MEMBER '
        + QUOTENAME(@backup_login) + N';';
    EXEC sys.sp_executesql @user_sql;
END;
