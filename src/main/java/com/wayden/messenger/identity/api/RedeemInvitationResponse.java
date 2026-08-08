package com.wayden.messenger.identity.api;

import java.util.UUID;

public record RedeemInvitationResponse(UUID userId, String username) {}
