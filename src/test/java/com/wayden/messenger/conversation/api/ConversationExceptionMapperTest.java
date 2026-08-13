package com.wayden.messenger.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.application.ConversationExceptions;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

final class ConversationExceptionMapperTest {

  @Test
  void internalFailureShouldRemainSafeForClientAndRetainRootCauseForAudit() {
    RequestAuditContext auditContext = new RequestAuditContext();
    auditContext.setRequestId("request-123");
    auditContext.setOperation("conversation.direct.create");
    ConversationExceptionMapper mapper = new ConversationExceptionMapper(auditContext);
    SQLException rootCause = new SQLException("Invalid object name 'messaging.conversation'");

    var response =
        mapper.toResponse(
            new ConversationExceptions.InternalException("create direct conversation", rootCause));

    assertEquals(500, response.getStatus());
    var problem = (ConversationExceptionMapper.ConversationProblem) response.getEntity();
    assertEquals("CONVERSATION_INTERNAL_ERROR", problem.code());
    assertEquals("Unexpected conversation error", problem.detail());

    var metadata = auditContext.getCustomAttributes();
    assertEquals("CONVERSATION_INTERNAL_ERROR", metadata.get("failureCode"));
    assertEquals(rootCause.getMessage(), metadata.get("failureMessage"));
    assertEquals(rootCause.getMessage(), metadata.get("failureDetail"));
    assertEquals(SQLException.class.getName(), metadata.get("failureRootCauseType"));
    assertTrue(metadata.get("failureLocation").contains("ConversationExceptionMapperTest"));
    assertTrue(
        metadata.get("failureRootCauseLocation").contains("ConversationExceptionMapperTest"));
  }
}
