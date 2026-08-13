package com.wayden.messenger.message.infrastructure;

import com.wayden.messenger.message.application.MessageIdGenerator;
import com.wayden.messenger.message.domain.MessageId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class UuidMessageIdGenerator implements MessageIdGenerator {
  @Override
  public MessageId newMessageId() {
    return new MessageId(UUID.randomUUID());
  }
}
