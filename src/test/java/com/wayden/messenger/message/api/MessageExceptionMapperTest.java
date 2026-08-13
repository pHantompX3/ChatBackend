package com.wayden.messenger.message.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.message.application.MessageExceptions;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

final class MessageExceptionMapperTest {

  @Test
  void internalFailureShouldRemainSafeForClientAndRetainRootCauseForAudit() {
    RequestAuditContext auditContext = new RequestAuditContext();
    auditContext.setRequestId("message-request-500");
    auditContext.setOperation("message.send");
    SQLException rootCause = new SQLException("Invalid object name 'messaging.message'");
    MessageExceptionMapper mapper = new MessageExceptionMapper(auditContext);

    var response =
        mapper.toResponse(new MessageExceptions.InternalException("insert message", rootCause));
    var problem = (MessageExceptionMapper.MessageProblem) response.getEntity();

    assertEquals(500, response.getStatus());
    assertEquals("MESSAGE_INTERNAL_ERROR", problem.code());
    assertEquals("Unexpected message error", problem.detail());
    assertFalse(problem.detail().contains(rootCause.getMessage()));
    assertEquals("MESSAGE_INTERNAL_ERROR", auditContext.getCustomAttributes().get("failureCode"));
    assertEquals(
        SQLException.class.getName(),
        auditContext.getCustomAttributes().get("failureRootCauseType"));
    assertNotNull(auditContext.getCustomAttributes().get("failureRootCauseLocation"));
  }
}
