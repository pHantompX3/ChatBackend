package com.wayden.messenger.common.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class AuditTransportHealthCheck implements HealthCheck {

  private final HttpAuditQueueDispatcher dispatcher;

  @Inject
  public AuditTransportHealthCheck(HttpAuditQueueDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public HealthCheckResponse call() {
    HttpAuditQueueDispatcher.AuditTransportStatus status = dispatcher.status();
    return HealthCheckResponse.named("audit-transport")
        .up()
        .withData("mode", status.mode())
        .withData("degraded", status.degraded())
        .withData("localQueueDepth", status.localQueueDepth())
        .withData("activeRabbitHost", status.activeRabbitHost())
        .build();
  }
}
