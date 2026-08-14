package com.wayden.messenger.delivery.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.wayden.messenger.common.api.ApiProblem;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.delivery.application.DeliveryExceptions;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

final class DeliveryExceptionMapperTest {

  @Test
  void internalFailureShouldRetainCauseForAuditButReturnSafeProblem() {
    var audit = new RequestAuditContext();
    var mapper = new DeliveryExceptionMapper(audit);

    var response =
        mapper.toResponse(
            new DeliveryExceptions.InternalException(
                "query delivery state", new SQLException("private SQL diagnostic")));

    assertEquals(500, response.getStatus());
    var problem = (ApiProblem) response.getEntity();
    assertEquals("DELIVERY_INTERNAL_ERROR", problem.code());
    assertEquals("Unexpected delivery error", problem.detail());
    assertEquals("private SQL diagnostic", audit.getCustomAttributes().get("failureMessage"));
    assertNotNull(audit.getCustomAttributes().get("failureRootCauseLocation"));
  }
}
