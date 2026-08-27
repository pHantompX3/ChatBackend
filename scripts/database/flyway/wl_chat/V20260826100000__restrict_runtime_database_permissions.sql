/*
  Replaces the historical broad runtime role membership with enumerated object permissions.
  Applied migrations remain immutable; this forward migration upgrades existing environments.

  Flyway placeholder:
    ${app_login}
*/
IF USER_ID(N'${app_login}') IS NULL
BEGIN
    THROW 51000, 'Runtime database user must exist before permissions are restricted.', 1;
END;

IF IS_ROLEMEMBER(N'db_datareader', N'${app_login}') = 1
BEGIN
    THROW 51001, 'Bootstrap operator must remove runtime from db_datareader before migration.', 1;
END;

IF IS_ROLEMEMBER(N'db_datawriter', N'${app_login}') = 1
BEGIN
    THROW 51002, 'Bootstrap operator must remove runtime from db_datawriter before migration.', 1;
END;

REVOKE EXECUTE TO [${app_login}];
REVOKE VIEW DEFINITION TO [${app_login}];

GRANT CONNECT TO [${app_login}];

GRANT SELECT, INSERT, UPDATE ON [identity].[user_account] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE ON [identity].[invitation] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE ON [identity].[session] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE, DELETE
    ON [identity].[authentication_rate_limit] TO [${app_login}];

GRANT SELECT, INSERT, UPDATE ON [messaging].[conversation] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE ON [messaging].[conversation_member] TO [${app_login}];
GRANT SELECT, INSERT ON [messaging].[direct_conversation_pair] TO [${app_login}];
GRANT SELECT, INSERT, UPDATE ON [messaging].[message] TO [${app_login}];

GRANT SELECT, INSERT ON [audit].[http_audit_event] TO [${app_login}];

DENY DELETE ON [identity].[user_account] TO [${app_login}];
DENY DELETE ON [identity].[invitation] TO [${app_login}];
DENY DELETE ON [identity].[session] TO [${app_login}];
DENY DELETE ON [messaging].[conversation] TO [${app_login}];
DENY DELETE ON [messaging].[conversation_member] TO [${app_login}];
DENY DELETE ON [messaging].[direct_conversation_pair] TO [${app_login}];
DENY DELETE ON [messaging].[message] TO [${app_login}];
DENY UPDATE, DELETE ON [audit].[http_audit_event] TO [${app_login}];

DENY SELECT, INSERT, UPDATE, DELETE
    ON [platform].[flyway_schema_history] TO [${app_login}];

DENY ALTER, CONTROL ON SCHEMA::[platform] TO [${app_login}];
DENY ALTER, CONTROL ON SCHEMA::[identity] TO [${app_login}];
DENY ALTER, CONTROL ON SCHEMA::[messaging] TO [${app_login}];
DENY ALTER, CONTROL ON SCHEMA::[audit] TO [${app_login}];
