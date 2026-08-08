package com.wayden.messenger.identity.api;

import jakarta.validation.constraints.NotBlank;

public record BootstrapAdminRequest(@NotBlank String username, @NotBlank String password) {}
