IF DATABASE_PRINCIPAL_ID(N'audit_monitor') IS NULL
    THROW 50001, 'The audit_monitor role must exist before correcting its connection permission.', 1;

-- A database-scoped DENY CONTROL also denies CONNECT through SQL Server's
-- permission hierarchy. Keep the role least-privileged through the explicit
-- object/schema denies from the preceding migration, while allowing the
-- monitor to enter wl_chat and read its single granted projection.
REVOKE CONTROL TO [audit_monitor];
GRANT CONNECT TO [audit_monitor];
GO
