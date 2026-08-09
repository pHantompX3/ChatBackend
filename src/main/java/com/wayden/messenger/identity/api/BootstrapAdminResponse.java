package com.wayden.messenger.identity.api;

import java.util.UUID;

public record BootstrapAdminResponse(UUID userId, String username) {}
