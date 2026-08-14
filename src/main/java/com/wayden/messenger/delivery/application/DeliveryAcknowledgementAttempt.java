package com.wayden.messenger.delivery.application;

import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryRepository.AcknowledgementAttempt;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DeliveryAcknowledgementAttempt {

  private final DeliveryRepository repository;

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public AcknowledgementAttempt acknowledgeDelivery(
      ConversationId conversationId, UserId actorId, long sequence) {
    return repository.acknowledgeDelivery(conversationId, actorId, sequence);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public AcknowledgementAttempt acknowledgeRead(
      ConversationId conversationId, UserId actorId, long sequence) {
    return repository.acknowledgeRead(conversationId, actorId, sequence);
  }
}
