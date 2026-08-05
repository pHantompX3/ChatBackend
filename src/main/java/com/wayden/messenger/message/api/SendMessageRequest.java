package com.wayden.messenger.message.api;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
    @NotBlank String conversationId, @NotBlank String senderUserId, @NotBlank String content) {}
