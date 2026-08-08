package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.UserId;

public record BootstrapAdminResult(UserId userId, String username) {}
