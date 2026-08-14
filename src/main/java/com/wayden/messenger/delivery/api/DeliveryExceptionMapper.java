package com.wayden.messenger.delivery.api;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.delivery.application.DeliveryExceptions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import org.jboss.logging.Logger;

@Provider
public class DeliveryExceptionMapper
    implements ExceptionMapper<DeliveryExceptions.DeliveryException> {

  private static final Logger LOG = Logger.getLogger(DeliveryExceptionMapper.class);
  private final RequestAuditContext auditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RequestAuditContext is CDI-managed request-scoped state.")
  public DeliveryExceptionMapper(RequestAuditContext auditContext) {
    this.auditContext = auditContext;
  }

  @Override
  public Response toResponse(DeliveryExceptions.DeliveryException exception) {
    ProblemMapping mapping = mapping(exception);
    auditContext.putCustomAttribute("eventType", "delivery.request.failed");
    auditContext.recordFailure(mapping.code(), exception);
    if (exception instanceof DeliveryExceptions.InternalException) {
      LOG.errorf(
          exception,
          "Delivery request failed requestId=%s operation=%s code=%s",
          auditContext.getRequestId(),
          auditContext.getOperation(),
          mapping.code());
    }
    return Response.status(mapping.status())
        .type("application/problem+json")
        .entity(
            new DeliveryProblem(
                URI.create("about:blank"),
                mapping.title(),
                mapping.status(),
                mapping.detail(),
                mapping.code()))
        .build();
  }

  private static ProblemMapping mapping(DeliveryExceptions.DeliveryException exception) {
    if (exception instanceof DeliveryExceptions.ValidationException) {
      return new ProblemMapping(
          400, "Delivery validation failed", "DELIVERY_VALIDATION_FAILED", exception.getMessage());
    }
    if (exception instanceof DeliveryExceptions.SequenceAheadException) {
      return new ProblemMapping(
          409, "Delivery sequence ahead", "DELIVERY_SEQUENCE_AHEAD", exception.getMessage());
    }
    if (exception instanceof DeliveryExceptions.StatusForbiddenException) {
      return new ProblemMapping(
          403, "Delivery status forbidden", "DELIVERY_STATUS_FORBIDDEN", exception.getMessage());
    }
    if (exception instanceof DeliveryExceptions.ResourceNotFoundException) {
      return new ProblemMapping(
          404,
          "Delivery resource not found",
          "DELIVERY_RESOURCE_NOT_FOUND",
          exception.getMessage());
    }
    return new ProblemMapping(
        500, "Delivery error", "DELIVERY_INTERNAL_ERROR", "Unexpected delivery error");
  }

  public record DeliveryProblem(URI type, String title, int status, String detail, String code) {}

  private record ProblemMapping(int status, String title, String code, String detail) {}
}
