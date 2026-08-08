package com.wayden.messenger.identity.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class IdentityApiConcurrencyIntegrationTest {

  @BeforeEach
  void resetIdentityTables() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    }
  }

  @Test
  void concurrentRedeemWithSameTokenShouldSucceedOnce() throws Exception {
    String adminUserId = bootstrapAdmin("Race Admin A");
    String invitationToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));

    List<AttemptResult> results =
        runConcurrently(
            2,
            () ->
                redeemInvitation(
                    invitationToken, "member-race-" + UUID.randomUUID(), "MemberPassw0rd!"));

    assertExactlyOneSuccess(results);
    List<AttemptResult> failures =
        results.stream().filter(result -> result.statusCode() != 200).collect(Collectors.toList());
    assertEquals(1, failures.size());
    assertEquals(422, failures.get(0).statusCode());
    assertEquals("INVITATION_ALREADY_REDEEMED", failures.get(0).problemCode());
  }

  @Test
  void concurrentRedeemWithSameUsernameShouldSucceedOnce() throws Exception {
    String adminUserId = bootstrapAdmin("Race Admin B");
    String firstToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));
    String secondToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));

    List<AttemptResult> results =
        runConcurrently(
            2,
            new ConcurrentAction[] {
              () -> redeemInvitation(firstToken, "member-race-username", "MemberPassw0rd!"),
              () -> redeemInvitation(secondToken, "member-race-username", "MemberPassw0rd!")
            });

    assertExactlyOneSuccess(results);
    List<AttemptResult> failures =
        results.stream().filter(result -> result.statusCode() != 200).collect(Collectors.toList());
    assertEquals(1, failures.size());
    assertEquals(409, failures.get(0).statusCode());
    assertEquals("DUPLICATE_USERNAME", failures.get(0).problemCode());
  }

  @Test
  void concurrentBootstrapAttemptsShouldSucceedOnce() throws Exception {
    List<AttemptResult> results =
        runConcurrently(2, () -> bootstrapAdminAttempt("bootstrap-race-admin", "AdminPassw0rd!"));

    assertExactlyOneSuccess(results);
    List<AttemptResult> failures =
        results.stream().filter(result -> result.statusCode() != 200).collect(Collectors.toList());
    assertEquals(1, failures.size());
    assertEquals(409, failures.get(0).statusCode());
    assertTrue(
        Objects.equals("BOOTSTRAP_ALREADY_COMPLETED", failures.get(0).problemCode())
            || Objects.equals("DUPLICATE_USERNAME", failures.get(0).problemCode()));
  }

  private static AttemptResult redeemInvitation(String token, String username, String password) {
    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "invitationToken", token,
                    "username", username,
                    "password", password))
            .when()
            .post("/api/v1/invitations/redeem");
    return toAttemptResult(response);
  }

  private static String bootstrapAdmin(String username) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "AdminPassw0rd!"))
        .when()
        .post("/api/v1/bootstrap/admin")
        .then()
        .statusCode(200)
        .extract()
        .path("userId");
  }

  private static AttemptResult bootstrapAdminAttempt(String username, String password) {
    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
            .when()
            .post("/api/v1/bootstrap/admin");
    return toAttemptResult(response);
  }

  private static String createInvitation(String actorUserId, Instant expiresAt) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", actorUserId, "expiresAt", expiresAt.toString()))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(200)
        .extract()
        .path("invitationToken");
  }

  private static AttemptResult toAttemptResult(Response response) {
    String problemCode = null;
    try {
      problemCode = response.path("code");
    } catch (Exception ignored) {
      // Non-problem responses do not include a code field.
    }
    return new AttemptResult(response.statusCode(), problemCode);
  }

  private static void assertExactlyOneSuccess(List<AttemptResult> results) {
    long successCount = results.stream().filter(result -> result.statusCode() == 200).count();
    assertEquals(1, successCount);
  }

  private static List<AttemptResult> runConcurrently(int participants, ConcurrentAction action)
      throws Exception {
    ConcurrentAction[] actions = new ConcurrentAction[participants];
    for (int i = 0; i < participants; i++) {
      actions[i] = action;
    }
    return runConcurrently(participants, actions);
  }

  private static List<AttemptResult> runConcurrently(int participants, ConcurrentAction[] actions)
      throws Exception {
    if (actions.length != participants) {
      throw new IllegalArgumentException("actions length must equal participants");
    }

    ExecutorService executor = Executors.newFixedThreadPool(participants);
    CountDownLatch ready = new CountDownLatch(participants);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<AttemptResult>> futures = new ArrayList<>();
      for (int i = 0; i < participants; i++) {
        ConcurrentAction selectedAction = actions[i];
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  boolean started = start.await(5, TimeUnit.SECONDS);
                  if (!started) {
                    throw new IllegalStateException("Timed out waiting to start concurrent action");
                  }
                  return selectedAction.run();
                }));
      }

      boolean allReady = ready.await(5, TimeUnit.SECONDS);
      if (!allReady) {
        throw new IllegalStateException("Timed out waiting for concurrent workers");
      }

      start.countDown();

      List<AttemptResult> results = new ArrayList<>();
      for (Future<AttemptResult> future : futures) {
        results.add(future.get(10, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  @FunctionalInterface
  private interface ConcurrentAction {
    AttemptResult run() throws Exception;
  }

  private record AttemptResult(int statusCode, String problemCode) {}
}
