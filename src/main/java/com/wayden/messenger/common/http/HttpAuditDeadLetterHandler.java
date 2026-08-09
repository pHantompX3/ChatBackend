package com.wayden.messenger.common.http;

public interface HttpAuditDeadLetterHandler {
  void handle(HttpAuditEvent event, Exception exception);
}
