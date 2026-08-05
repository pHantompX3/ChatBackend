package com.wayden.messenger.message.api;

import java.time.Instant;

public record MessageResponse(
    String messageId,
    String conversationId,
    String senderUserId,
    String content,
    Instant createdAt) {}
