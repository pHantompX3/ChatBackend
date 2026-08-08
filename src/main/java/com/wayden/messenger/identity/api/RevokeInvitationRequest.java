package com.wayden.messenger.identity.api;

import jakarta.validation.constraints.NotBlank;

public record RevokeInvitationRequest(@NotBlank String actorUserId) {}
