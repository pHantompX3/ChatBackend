package com.wayden.messenger.common.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HttpAuditQueueDispatcherTest {

  @Test
  void submitShouldFailOpenAndSendToDeadLetterWhenSyncPersistenceFails() {
    AtomicInteger deadLetterCount = new AtomicInteger();

    HttpAuditEventSink failingSink =
        event -> {
          throw new IllegalStateException("sink failure");
        };
    HttpAuditDeadLetterHandler deadLetterHandler =
        (event, exception) -> deadLetterCount.incrementAndGet();

    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(failingSink, deadLetterHandler, false, 8);

    assertDoesNotThrow(() -> dispatcher.submit(sampleEvent()));
    assertEquals(1, deadLetterCount.get());
  }

  @Test
  void asyncWorkerShouldRouteSinkFailuresToDeadLetter() throws Exception {
    CountDownLatch deadLetterSeen = new CountDownLatch(1);

    HttpAuditEventSink failingSink =
        event -> {
          throw new IllegalStateException("sink failure");
        };
    HttpAuditDeadLetterHandler deadLetterHandler = (event, exception) -> deadLetterSeen.countDown();

    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(failingSink, deadLetterHandler, true, 8);

    dispatcher.start();
    try {
      assertDoesNotThrow(() -> dispatcher.submit(sampleEvent()));
      assertTrue(deadLetterSeen.await(2, TimeUnit.SECONDS));
    } finally {
      dispatcher.shutdown();
    }
  }

  @Test
  void queueFullShouldSendOverflowEventsToDeadLetter() throws Exception {
    CountDownLatch sinkStarted = new CountDownLatch(1);
    CountDownLatch releaseSink = new CountDownLatch(1);
    CountDownLatch deadLetterSeen = new CountDownLatch(1);

    HttpAuditEventSink blockingSink =
        event -> {
          sinkStarted.countDown();
          try {
            assertTrue(releaseSink.await(2, TimeUnit.SECONDS));
          } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
          }
        };
    HttpAuditDeadLetterHandler deadLetterHandler = (event, exception) -> deadLetterSeen.countDown();

    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(blockingSink, deadLetterHandler, true, 1);

    dispatcher.start();
    try {
      dispatcher.submit(sampleEvent());
      assertTrue(sinkStarted.await(2, TimeUnit.SECONDS));

      dispatcher.submit(sampleEvent());
      dispatcher.submit(sampleEvent());

      assertTrue(deadLetterSeen.await(2, TimeUnit.SECONDS));
    } finally {
      releaseSink.countDown();
      dispatcher.shutdown();
    }
  }

  @Test
  void startShouldRetryRabbitConnectionUntilAvailable() throws Exception {
    AtomicInteger initializationAttempts = new AtomicInteger();
    CountDownLatch connectionSucceeded = new CountDownLatch(1);

    HttpAuditQueueDispatcher dispatcher =
        new HttpAuditQueueDispatcher(
            event -> {},
            (event, exception) -> {},
            false,
            8,
            () -> {
              int attempt = initializationAttempts.incrementAndGet();
              if (attempt >= 2) {
                connectionSucceeded.countDown();
                return true;
              }
              return false;
            });

    dispatcher.start();
    try {
      assertTrue(connectionSucceeded.await(3, TimeUnit.SECONDS));
      assertTrue(isRabbitActive(dispatcher));
    } finally {
      dispatcher.shutdown();
    }
  }

  private static boolean isRabbitActive(HttpAuditQueueDispatcher dispatcher) throws Exception {
    Field rabbitActiveField = HttpAuditQueueDispatcher.class.getDeclaredField("rabbitActive");
    rabbitActiveField.setAccessible(true);
    return rabbitActiveField.getBoolean(dispatcher);
  }

  private static HttpAuditEvent sampleEvent() {
    Instant now = Instant.parse("2026-08-08T14:00:00Z");
    return new HttpAuditEvent(
        UUID.randomUUID(),
        "1.0",
        "http.request.completed",
        now,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "identity.invitation.create",
        "POST",
        "identity.invitation.create",
        "/api/v1/invitations",
        "-",
        200,
        null,
        12,
        now.minusMillis(12),
        now,
        null,
        null,
        null,
        "invitation",
        UUID.randomUUID().toString(),
        "127.0.0.1",
        "127.0.0.1",
        "vertx-remote-address",
        "curl/8.0",
        "cli",
        "macOS",
        "-",
        "macos",
        "curl",
        Map.of("accept", "*/*"),
        Map.of("content-type", "application/json"),
        null,
        null,
        Map.of("identityEvent", "invitation.created"),
        new byte[] {1, 2, 3});
  }
}
