package com.wayden.messenger.message.application;

import com.wayden.messenger.message.domain.MessageId;

public interface MessageIdGenerator {
  MessageId newMessageId();
}
