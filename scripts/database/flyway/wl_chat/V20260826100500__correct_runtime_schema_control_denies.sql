/*
  Schema-level DENY CONTROL dominates the intended object-level DML grants. Remove that over-broad
  deny while retaining DENY ALTER and the explicit object permission model.

  Flyway placeholder:
    ${app_login}
*/
REVOKE CONTROL ON SCHEMA::[platform] FROM [${app_login}];
REVOKE CONTROL ON SCHEMA::[identity] FROM [${app_login}];
REVOKE CONTROL ON SCHEMA::[messaging] FROM [${app_login}];
REVOKE CONTROL ON SCHEMA::[audit] FROM [${app_login}];

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
