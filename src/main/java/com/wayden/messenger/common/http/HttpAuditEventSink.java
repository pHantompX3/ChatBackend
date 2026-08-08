package com.wayden.messenger.common.http;

public interface HttpAuditEventSink {
  void persist(HttpAuditEvent event);
}
