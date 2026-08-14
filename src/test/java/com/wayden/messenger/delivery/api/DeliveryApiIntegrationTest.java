package com.wayden.messenger.delivery.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.delivery.application.DeliveryRepository;
import com.wayden.messenger.delivery.application.DeliveryRepository.AcknowledgementAttempt;
import com.wayden.messenger.identity.domain.UserId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class DeliveryApiIntegrationTest {

  private static final String ADMIN_PASSWORD = "AdminPassw0rd!";
  private static final String MEMBER_PASSWORD = "MemberPassw0rd!";

  @Inject DeliveryRepository deliveryRepository;

  @BeforeEach
  void resetTables() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
      statement.executeUpdate("DELETE FROM [messaging].[message]");
      statement.executeUpdate("DELETE FROM [messaging].[direct_conversation_pair]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation_member]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation]");
      statement.executeUpdate("DELETE FROM [identity].[session]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    }
  }

  @Test
  void directPositionsUnreadAndStatusShouldFollowExplicitAcknowledgements() {
    Account owner = bootstrapAdmin("Delivery Owner");
    Account member = inviteMember(owner, "Delivery Member");
    String conversationId = createDirect(owner, member.userId());
    Message first = send(owner, conversationId, "first synthetic delivery body");
    Message second = send(owner, conversationId, "second synthetic delivery body");

    position(member, conversationId, 200)
        .body("latestSequence", equalTo(2))
        .body("lastDeliveredSequence", equalTo(0))
        .body("lastReadSequence", equalTo(0))
        .body("unreadCount", equalTo(2));
    position(owner, conversationId, 200).body("unreadCount", equalTo(0));

    acknowledge(member, conversationId, "delivery-position", 1, 204);
    status(owner, conversationId, first.id(), 200)
        .body("serverAccepted", equalTo(true))
        .body("recipientCount", equalTo(1))
        .body("deliveredCount", equalTo(1))
        .body("readCount", equalTo(0))
        .body("allDelivered", equalTo(true))
        .body("allRead", equalTo(false));

    acknowledge(member, conversationId, "delivery-position", 0, 204);
    acknowledge(member, conversationId, "read-position", 1, 204);
    position(member, conversationId, 200)
        .body("lastDeliveredSequence", equalTo(1))
        .body("lastReadSequence", equalTo(1))
        .body("unreadCount", equalTo(1));
    status(owner, conversationId, first.id(), 200)
        .body("deliveredCount", equalTo(1))
        .body("readCount", equalTo(1))
        .body("allRead", equalTo(true));

    deleteMessage(owner, conversationId, second.id());
    position(member, conversationId, 200).body("unreadCount", equalTo(0));
    status(owner, conversationId, second.id(), 200)
        .body("sequence", equalTo(2))
        .body("serverAccepted", equalTo(true));
  }

  @Test
  void groupStatusShouldUseCurrentRecipientsAndEnforceSenderVisibility() {
    Account platformAdmin = bootstrapAdmin("Receipt Platform Admin");
    Account owner = inviteMember(platformAdmin, "Receipt Group Owner");
    Account first = inviteMember(platformAdmin, "Receipt First Member");
    Account second = inviteMember(platformAdmin, "Receipt Second Member");
    Account outsider = inviteMember(platformAdmin, "Receipt Outsider");
    String conversationId =
        createGroup(owner, "Receipt Group", List.of(first.userId(), second.userId()));
    Message message = send(owner, conversationId, "group receipt body");

    status(owner, conversationId, message.id(), 200)
        .body("recipientCount", equalTo(2))
        .body("deliveredCount", equalTo(0))
        .body("allDelivered", equalTo(false));
    acknowledge(first, conversationId, "read-position", 1, 204);
    status(owner, conversationId, message.id(), 200)
        .body("recipientCount", equalTo(2))
        .body("deliveredCount", equalTo(1))
        .body("readCount", equalTo(1))
        .body("allDelivered", equalTo(false));

    status(first, conversationId, message.id(), 403)
        .body("code", equalTo("DELIVERY_STATUS_FORBIDDEN"));
    status(outsider, conversationId, message.id(), 404)
        .body("code", equalTo("DELIVERY_RESOURCE_NOT_FOUND"));
    status(platformAdmin, conversationId, message.id(), 404)
        .body("code", equalTo("DELIVERY_RESOURCE_NOT_FOUND"));

    removeMember(owner, conversationId, second.userId());
    status(owner, conversationId, message.id(), 200)
        .body("recipientCount", equalTo(1))
        .body("deliveredCount", equalTo(1))
        .body("readCount", equalTo(1))
        .body("allDelivered", equalTo(true))
        .body("allRead", equalTo(true));
    position(second, conversationId, 404).body("code", equalTo("DELIVERY_RESOURCE_NOT_FOUND"));
  }

  @Test
  void validationAuthenticationAndPrivacyShouldBeStable() {
    Account owner = bootstrapAdmin("Delivery Validation Owner");
    Account member = inviteMember(owner, "Delivery Validation Member");
    Account outsider = inviteMember(owner, "Delivery Validation Outsider");
    String conversationId = createDirect(owner, member.userId());

    acknowledge(member, conversationId, "delivery-position", 1, 409)
        .body("code", equalTo("DELIVERY_SEQUENCE_AHEAD"));
    acknowledge(outsider, conversationId, "delivery-position", 1, 404)
        .body("code", equalTo("DELIVERY_RESOURCE_NOT_FOUND"));
    acknowledge(member, conversationId, "delivery-position", -1, 400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    acknowledgeRaw(member, conversationId, "delivery-position", "{\"sequence\":0.5}", 400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    acknowledgeRaw(member, conversationId, "delivery-position", "{\"sequence\":\"0\"}", 400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    acknowledgeRaw(member, conversationId, "delivery-position", "{}", 400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    acknowledgeRaw(
            member, conversationId, "delivery-position", "{\"sequence\":9223372036854775808}", 400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(member.token()))
        .body("{\"sequence\":null}")
        .when()
        .put("/api/v1/conversations/{conversationId}/read-position", conversationId)
        .then()
        .statusCode(400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(member.token()))
        .body("{\"sequence\":0,\"userId\":\"" + member.userId() + "\"}")
        .when()
        .put("/api/v1/conversations/{conversationId}/read-position", conversationId)
        .then()
        .statusCode(400);
    given()
        .header("Authorization", bearer(member.token()))
        .when()
        .get("/api/v1/conversations/not-a-uuid/position")
        .then()
        .statusCode(400)
        .body("code", equalTo("DELIVERY_VALIDATION_FAILED"));
    given()
        .when()
        .get("/api/v1/conversations/{conversationId}/position", conversationId)
        .then()
        .statusCode(401);
  }

  @Test
  void concurrentAcknowledgementsShouldFinishAtMaximumWithoutRegressingRead() throws Exception {
    Account owner = bootstrapAdmin("Concurrent Delivery Owner");
    Account member = inviteMember(owner, "Concurrent Delivery Member");
    String conversationId = createDirect(owner, member.userId());
    send(owner, conversationId, "sequence one");
    send(owner, conversationId, "sequence two");
    send(owner, conversationId, "sequence three");
    List<AcknowledgementCall> calls =
        List.of(
            new AcknowledgementCall("delivery-position", 1),
            new AcknowledgementCall("read-position", 3),
            new AcknowledgementCall("delivery-position", 2),
            new AcknowledgementCall("read-position", 1));
    CountDownLatch ready = new CountDownLatch(calls.size());
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(calls.size())) {
      var futures =
          calls.stream()
              .map(
                  call ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            assertTrue(start.await(3, TimeUnit.SECONDS));
                            return acknowledge(
                                    member, conversationId, call.route(), call.sequence(), 204)
                                .extract()
                                .statusCode();
                          }))
              .toList();
      assertTrue(ready.await(3, TimeUnit.SECONDS));
      start.countDown();
      for (var future : futures) {
        assertEquals(204, future.get(10, TimeUnit.SECONDS));
      }
    }

    position(member, conversationId, 200)
        .body("latestSequence", equalTo(3))
        .body("lastDeliveredSequence", equalTo(3))
        .body("lastReadSequence", equalTo(3))
        .body("unreadCount", equalTo(0));
  }

  @Test
  void memberRemovalShouldWaitForAnAcknowledgementThatAlreadyHoldsTheMembershipLock()
      throws Exception {
    Account owner = bootstrapAdmin("Receipt Removal Owner");
    Account member = inviteMember(owner, "Receipt Removal Member");
    String conversationId = createGroup(owner, "Receipt Removal Group", List.of(member.userId()));
    send(owner, conversationId, "receipt before removal");
    CountDownLatch acknowledged = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var acknowledgementFuture =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          var result =
                              deliveryRepository.acknowledgeDelivery(
                                  new ConversationId(UUID.fromString(conversationId)),
                                  new UserId(UUID.fromString(member.userId())),
                                  1);
                          assertTrue(result instanceof AcknowledgementAttempt.Acknowledged);
                          acknowledged.countDown();
                          await(release);
                        });
                return null;
              });
      assertTrue(acknowledged.await(3, TimeUnit.SECONDS));
      var removalFuture =
          executor.submit(
              () ->
                  given()
                      .header("Authorization", bearer(owner.token()))
                      .when()
                      .delete(
                          "/api/v1/conversations/{conversationId}/members/{userId}",
                          conversationId,
                          member.userId())
                      .statusCode());

      try {
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.concurrent.TimeoutException.class,
            () -> removalFuture.get(250, TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      acknowledgementFuture.get(3, TimeUnit.SECONDS);
      assertEquals(204, removalFuture.get(3, TimeUnit.SECONDS));
    }

    assertEquals(1, departedDeliveryPosition(conversationId, member.userId()));
    position(member, conversationId, 404).body("code", equalTo("DELIVERY_RESOURCE_NOT_FOUND"));
  }

  private static ValidatableResponse acknowledge(
      Account actor, String conversationId, String route, long sequence, int expectedStatus) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("sequence", sequence))
        .when()
        .put("/api/v1/conversations/{conversationId}/{route}", conversationId, route)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse acknowledgeRaw(
      Account actor, String conversationId, String route, String body, int expectedStatus) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(body)
        .when()
        .put("/api/v1/conversations/{conversationId}/{route}", conversationId, route)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse position(
      Account actor, String conversationId, int expectedStatus) {
    return given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/position", conversationId)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse status(
      Account actor, String conversationId, String messageId, int expectedStatus) {
    return given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .get(
            "/api/v1/conversations/{conversationId}/messages/{messageId}/status",
            conversationId,
            messageId)
        .then()
        .statusCode(expectedStatus);
  }

  private static Message send(Account actor, String conversationId, String body) {
    var json =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", bearer(actor.token()))
            .body(Map.of("clientMessageId", UUID.randomUUID().toString(), "body", body))
            .when()
            .post("/api/v1/conversations/{conversationId}/messages", conversationId)
            .then()
            .statusCode(201)
            .extract()
            .jsonPath();
    return new Message(json.getString("messageId"), json.getLong("sequenceNumber"));
  }

  private static void deleteMessage(Account actor, String conversationId, String messageId) {
    given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .delete(
            "/api/v1/conversations/{conversationId}/messages/{messageId}",
            conversationId,
            messageId)
        .then()
        .statusCode(204);
  }

  private static void removeMember(Account actor, String conversationId, String userId) {
    given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .delete("/api/v1/conversations/{conversationId}/members/{userId}", conversationId, userId)
        .then()
        .statusCode(204);
  }

  private static String createDirect(Account actor, String targetUserId) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("targetUserId", targetUserId))
        .when()
        .post("/api/v1/conversations/direct")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getString("conversationId");
  }

  private static String createGroup(Account owner, String title, List<String> initialMemberIds) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(owner.token()))
        .body(Map.of("title", title, "initialMemberIds", initialMemberIds))
        .when()
        .post("/api/v1/conversations/groups")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getString("conversationId");
  }

  private static Account bootstrapAdmin(String username) {
    String userId =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", ADMIN_PASSWORD))
            .when()
            .post("/api/v1/bootstrap/admin")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("userId");
    return new Account(userId, login(username, ADMIN_PASSWORD));
  }

  private static Account inviteMember(Account admin, String username) {
    String invitationToken =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", bearer(admin.token()))
            .body(Map.of("actorUserId", admin.userId(), "expiresAt", "2099-01-01T00:00:00Z"))
            .when()
            .post("/api/v1/invitations")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("invitationToken");
    String userId =
        given()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "invitationToken",
                    invitationToken,
                    "username",
                    username,
                    "password",
                    MEMBER_PASSWORD))
            .when()
            .post("/api/v1/invitations/redeem")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("userId");
    return new Account(userId, login(username, MEMBER_PASSWORD));
  }

  private static String login(String username, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", password))
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("token");
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static long departedDeliveryPosition(String conversationId, String userId)
      throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement =
            connection.prepareStatement(
                "SELECT last_delivered_sequence FROM messaging.conversation_member "
                    + "WHERE conversation_id = ? AND user_id = ? AND left_at IS NOT NULL")) {
      statement.setObject(1, UUID.fromString(conversationId));
      statement.setObject(2, UUID.fromString(userId));
      try (var result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getLong(1);
      }
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(3, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release acknowledgement transaction");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for acknowledgement release", exception);
    }
  }

  private record Account(String userId, String token) {}

  private record Message(String id, long sequence) {}

  private record AcknowledgementCall(String route, long sequence) {}
}
