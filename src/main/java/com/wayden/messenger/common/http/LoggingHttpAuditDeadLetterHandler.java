package com.wayden.messenger.common.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoggingHttpAuditDeadLetterHandler implements HttpAuditDeadLetterHandler {

  private static final Logger LOG = Logger.getLogger(LoggingHttpAuditDeadLetterHandler.class);

  private final ObjectMapper objectMapper;

  @Override
  public void handle(HttpAuditEvent event, Exception exception) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", "audit.deadletter");
    payload.put("requestId", event.requestId());
    payload.put("traceId", event.traceId());
    payload.put("eventType", event.eventType());
    payload.put("operation", event.operation());
    payload.put("reason", exception.getClass().getSimpleName());
    payload.put("message", exception.getMessage());

    LOG.warnf("AUDIT DLQ %s", toJson(payload));
  }

  private String toJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ignored) {
      return payload.toString();
    }
  }
}
