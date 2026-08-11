package com.wayden.messenger.session.api;

import jakarta.validation.constraints.NotBlank;

public record SessionLoginRequest(@NotBlank String username, @NotBlank String password) {}
