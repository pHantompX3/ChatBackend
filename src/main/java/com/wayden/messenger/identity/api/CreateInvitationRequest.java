package com.wayden.messenger.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateInvitationRequest(@NotBlank String actorUserId, @NotNull Instant expiresAt) {}
