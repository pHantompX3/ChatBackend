package com.wayden.messenger.bootstrap.config;

public class RuntimeFileConfigSource extends FileBackedConfigSource {

    private static final int ORDINAL = 260;
    private static final String OVERRIDE_ENV = "WL_CHAT_CONFIG_FILE";
    private static final String DEFAULT_CONFIG_PATH = "config/application.properties";

    public RuntimeFileConfigSource() {
        super("wl-chat-runtime-config", resolvePath(OVERRIDE_ENV, DEFAULT_CONFIG_PATH));
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}