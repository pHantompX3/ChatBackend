package com.wayden.messenger.bootstrap.config;

public class LocalSecretsConfigSource extends FileBackedConfigSource {

    private static final int ORDINAL = 275;
    private static final String OVERRIDE_ENV = "WL_CHAT_SECRETS_FILE";
    private static final String DEFAULT_SECRETS_PATH = "scripts/config/local.secrets.env";

    public LocalSecretsConfigSource() {
        super("wl-chat-local-secrets", resolvePath(OVERRIDE_ENV, DEFAULT_SECRETS_PATH));
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}