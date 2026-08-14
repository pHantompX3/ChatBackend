package com.wayden.messenger.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class HttpAuditFilterTest {

  @Test
  void responseAuditShouldCarryFailureDiagnosticsIntoDispatchedEvent() throws Exception {
    RequestAuditContext auditContext = new RequestAuditContext();
    auditContext.setRequestId("request-500");
    auditContext.setTraceId("trace-500");
    auditContext.setOperation("conversation.group.create");
    auditContext.setMethod("POST");
    auditContext.setPath("/api/v1/conversations/groups");
    auditContext.setQuery("-");
    auditContext.recordFailure(
        "CONVERSATION_INTERNAL_ERROR", new SQLException("Invalid object name 'conversation'"));

    AtomicReference<HttpAuditEvent> captured = new AtomicReference<>();
    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(captured::set, (event, exception) -> {}, false, 8);
    HttpAuditFilter filter = new HttpAuditFilter(auditContext, new ObjectMapper(), dispatcher);

    filter.filter(requestContext(), responseContext(500));

    HttpAuditEvent event = captured.get();
    assertNotNull(event);
    assertEquals("CONVERSATION_INTERNAL_ERROR", event.responseCode());
    assertEquals("CONVERSATION_INTERNAL_ERROR", event.errorCode());
    assertEquals("Invalid object name 'conversation'", event.errorMessage());
    assertEquals(SQLException.class.getName(), event.metadata().get("failureRootCauseType"));
    assertNotNull(event.metadata().get("failureLocation"));
    assertNotNull(event.metadata().get("failureRootCauseLocation"));
  }

  @Test
  void responseAuditShouldUseConversationTargetWhenNoMessageTargetExists() throws Exception {
    RequestAuditContext auditContext = new RequestAuditContext();
    auditContext.setRequestId("request-position");
    auditContext.setOperation("delivery.position.get");
    auditContext.setMethod("GET");
    auditContext.setPath("/api/v1/conversations/id/position");
    auditContext.setQuery("-");
    auditContext.setResponseStatus(200);
    String conversationId = java.util.UUID.randomUUID().toString();
    auditContext.putCustomAttribute("targetConversationId", conversationId);

    AtomicReference<HttpAuditEvent> captured = new AtomicReference<>();
    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(captured::set, (event, exception) -> {}, false, 8);
    HttpAuditFilter filter = new HttpAuditFilter(auditContext, new ObjectMapper(), dispatcher);

    filter.filter(requestContext(), responseContext(200));

    assertEquals("conversation", captured.get().targetType());
    assertEquals(conversationId, captured.get().targetId());
  }

  private static ContainerRequestContext requestContext() {
    return (ContainerRequestContext)
        Proxy.newProxyInstance(
            HttpAuditFilterTest.class.getClassLoader(),
            new Class<?>[] {ContainerRequestContext.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getProperty" -> null;
                  case "getHeaders" -> new MultivaluedHashMap<String, String>();
                  default -> defaultValue(method.getReturnType());
                });
  }

  private static ContainerResponseContext responseContext(int status) {
    return (ContainerResponseContext)
        Proxy.newProxyInstance(
            HttpAuditFilterTest.class.getClassLoader(),
            new Class<?>[] {ContainerResponseContext.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getStatus" -> status;
                  case "getLength" -> -1;
                  case "getHeaders" -> new MultivaluedHashMap<String, Object>();
                  default -> defaultValue(method.getReturnType());
                });
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    return 0;
  }
}
