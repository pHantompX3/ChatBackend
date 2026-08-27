SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
USE [master];

DECLARE @backup_login sysname = N'$(BACKUP_LOGIN)';
DECLARE @restore_login sysname = N'$(RESTORE_LOGIN)';
DECLARE @master_key_password_hex varchar(512) = '$(MASTER_KEY_PASSWORD_HEX)';
DECLARE @certificate_password_hex varchar(512) = '$(CERTIFICATE_PASSWORD_HEX)';
DECLARE @master_key_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@master_key_password_hex"))',
        'varbinary(256)'));
DECLARE @certificate_password nvarchar(128) = CONVERT(
    nvarchar(128),
    CAST(N'' AS xml).value(
        'xs:hexBinary(sql:variable("@certificate_password_hex"))',
        'varbinary(256)'));
DECLARE @sql nvarchar(max);

IF @master_key_password IS NULL OR @certificate_password IS NULL
BEGIN
    THROW 50002, 'A backup-encryption password could not be decoded.', 1;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.symmetric_keys WHERE [name] = N'##MS_DatabaseMasterKey##')
BEGIN
    SET @sql = N'CREATE MASTER KEY ENCRYPTION BY PASSWORD = '
        + QUOTENAME(@master_key_password, '''') + N';';
    EXEC sys.sp_executesql @sql;
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.certificates WHERE [name] = N'wl_chat_backup_certificate')
BEGIN
    CREATE CERTIFICATE [wl_chat_backup_certificate]
        WITH SUBJECT = N'ChatBackend encrypted backup certificate';
END;

SET @sql = N'GRANT VIEW DEFINITION ON CERTIFICATE::[wl_chat_backup_certificate] TO '
    + QUOTENAME(@backup_login) + N';';
EXEC sys.sp_executesql @sql;
SET @sql = N'GRANT VIEW DEFINITION ON CERTIFICATE::[wl_chat_backup_certificate] TO '
    + QUOTENAME(@restore_login) + N';';
EXEC sys.sp_executesql @sql;

IF LOWER(N'$(EXPORT_CERTIFICATE)') = N'true'
BEGIN
    SET @sql = N'BACKUP CERTIFICATE [wl_chat_backup_certificate] '
        + N'TO FILE = N''/var/opt/mssql/backup/wl_chat_backup_certificate.cer'' '
        + N'WITH PRIVATE KEY (FILE = N''/var/opt/mssql/backup/wl_chat_backup_certificate.pvk'', '
        + N'ENCRYPTION BY PASSWORD = ' + QUOTENAME(@certificate_password, '''') + N');';
    EXEC sys.sp_executesql @sql;
END;
