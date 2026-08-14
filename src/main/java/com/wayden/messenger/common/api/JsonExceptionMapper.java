package com.wayden.messenger.common.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wayden.messenger.common.http.RequestAuditContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER - 200)
public class JsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {

  private final ApiProblemFactory problems;

  @Inject
  public JsonExceptionMapper(RequestAuditContext auditContext) {
    this.problems = new ApiProblemFactory(auditContext);
  }

  @Override
  public Response toResponse(JsonProcessingException exception) {
    return problems.response(
        400, "Malformed JSON", "MALFORMED_JSON", "Request body is not valid JSON");
  }
}
