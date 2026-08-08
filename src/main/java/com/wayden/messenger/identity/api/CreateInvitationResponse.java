package com.wayden.messenger.identity.api;

import java.util.UUID;

public record CreateInvitationResponse(UUID invitationId, String invitationToken) {}
