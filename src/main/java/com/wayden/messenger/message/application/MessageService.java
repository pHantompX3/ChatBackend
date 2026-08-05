package com.wayden.messenger.message.application;

import com.wayden.messenger.message.api.MessageResponse;
import com.wayden.messenger.message.api.SendMessageRequest;

import java.util.List;

public interface MessageService {
    MessageResponse send(SendMessageRequest request);

    List<MessageResponse> listByConversation(String conversationId, int limit);
}
