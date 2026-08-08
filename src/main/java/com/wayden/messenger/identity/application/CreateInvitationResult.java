package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.InvitationId;

public record CreateInvitationResult(InvitationId invitationId, String rawToken) {}
