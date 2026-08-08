package com.wayden.messenger.identity.api;

import jakarta.validation.constraints.NotBlank;

public record RedeemInvitationRequest(
    @NotBlank String invitationToken, @NotBlank String username, @NotBlank String password) {}
