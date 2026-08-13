package com.wayden.messenger.message.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageRepository;
import com.wayden.messenger.message.domain.MessageId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class MessageApiIntegrationTest {

  private static final String ADMIN_PASSWORD = "AdminPassw0rd!";
  private static final String MEMBER_PASSWORD = "MemberPassw0rd!";

  @Inject MessageRepository messageRepository;

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
  void sendRetryConflictAndHistoryShouldFollowDurableContract() {
    Account owner = bootstrapAdmin("Durable Owner");
    Account member = inviteMember(owner, "Durable Member");
    String directId = createDirect(owner, member.userId());
    String secondConversationId = createGroup(owner, "Second Durable Group", List.of());
    String clientMessageId = UUID.randomUUID().toString();

    var accepted =
        send(owner, directId, clientMessageId, "  synthetic first message  ", 201)
            .header("Location", notNullValue())
            .body("senderId", equalTo(owner.userId()))
            .body("body", equalTo("  synthetic first message  "))
            .body("sequenceNumber", equalTo(1))
            .extract()
            .jsonPath();
    String messageId = accepted.getString("messageId");

    send(owner, directId, clientMessageId, "ignored retry body", 200)
        .body("messageId", equalTo(messageId))
        .body("body", equalTo("  synthetic first message  "))
        .body("sequenceNumber", equalTo(1));

    send(owner, secondConversationId, clientMessageId, "conflicting conversation", 409)
        .body("code", equalTo("MESSAGE_IDEMPOTENCY_CONFLICT"));

    send(owner, directId, UUID.randomUUID().toString(), "second message", 201)
        .body("sequenceNumber", equalTo(2));

    var firstPage =
        given()
            .header("Authorization", bearer(member.token()))
            .queryParam("afterSequence", 0)
            .queryParam("limit", 1)
            .when()
            .get("/api/v1/conversations/{conversationId}/messages", directId)
            .then()
            .statusCode(200)
            .body("items", hasSize(1))
            .body("items[0].sequenceNumber", equalTo(1))
            .body("nextAfterSequence", equalTo(1));
    firstPage.extract();

    given()
        .header("Authorization", bearer(member.token()))
        .queryParam("afterSequence", 1)
        .queryParam("limit", 1)
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", directId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].sequenceNumber", equalTo(2))
        .body("nextAfterSequence", equalTo(null));

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(owner.token()))
        .body(Map.of("clientMessageId", UUID.randomUUID().toString(), "body", "old route"))
        .when()
        .post("/api/v1/messages")
        .then()
        .statusCode(404);
  }

  @Test
  void editAndSoftDeleteShouldEnforceSenderAndGroupModerationRules() throws Exception {
    Account owner = bootstrapAdmin("Moderation Owner");
    Account member = inviteMember(owner, "Moderation Member");
    String conversationId = createGroup(owner, "Moderation Group", List.of(member.userId()));
    String privateBody = "synthetic-private-body-should-not-be-audited";
    String messageId =
        send(member, conversationId, UUID.randomUUID().toString(), privateBody, 201)
            .extract()
            .jsonPath()
            .getString("messageId");

    edit(owner, conversationId, messageId, "owner may not edit", 403)
        .body("code", equalTo("MESSAGE_EDIT_FORBIDDEN"));
    edit(member, conversationId, messageId, "corrected synthetic message", 200)
        .body("editedAt", notNullValue())
        .body("body", equalTo("corrected synthetic message"));

    delete(owner, conversationId, messageId, 204);
    delete(owner, conversationId, messageId, 204);

    given()
        .header("Authorization", bearer(member.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].messageId", equalTo(messageId))
        .body("items[0].body", equalTo(null))
        .body("items[0].deletedAt", notNullValue());

    edit(member, conversationId, messageId, "cannot revive", 403)
        .body("code", equalTo("MESSAGE_EDIT_FORBIDDEN"));

    String audit = latestAuditMetadata("message.administratively.deleted");
    assertTrue(audit.contains(messageId));
    assertTrue(audit.contains(member.userId()));
    assertFalse(audit.contains(privateBody));
  }

  @Test
  void activeMembershipShouldBoundEveryOperation() {
    Account owner = bootstrapAdmin("Membership Owner");
    Account member = inviteMember(owner, "Membership Member");
    Account outsider = inviteMember(owner, "Membership Outsider");
    String conversationId = createGroup(owner, "Membership Group", List.of(member.userId()));
    String messageId =
        send(member, conversationId, UUID.randomUUID().toString(), "membership message", 201)
            .extract()
            .jsonPath()
            .getString("messageId");

    given()
        .header("Authorization", bearer(outsider.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(404)
        .body("code", equalTo("MESSAGE_ACCESS_DENIED"));

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .delete(
            "/api/v1/conversations/{conversationId}/members/{userId}",
            conversationId,
            member.userId())
        .then()
        .statusCode(204);

    send(member, conversationId, UUID.randomUUID().toString(), "post-removal", 404)
        .body("code", equalTo("MESSAGE_ACCESS_DENIED"));
    given()
        .header("Authorization", bearer(member.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(404)
        .body("code", equalTo("MESSAGE_ACCESS_DENIED"));
    edit(member, conversationId, messageId, "post-removal edit", 404)
        .body("code", equalTo("MESSAGE_ACCESS_DENIED"));
  }

  @Test
  void directRecipientAndOrdinaryGroupMemberCannotDeleteAnotherSendersMessage() {
    Account owner = bootstrapAdmin("Delete Policy Owner");
    Account member = inviteMember(owner, "Delete Policy Member");
    Account ordinary = inviteMember(owner, "Delete Policy Ordinary");
    String directId = createDirect(owner, member.userId());
    String directMessageId =
        send(owner, directId, UUID.randomUUID().toString(), "direct sender body", 201)
            .extract()
            .jsonPath()
            .getString("messageId");

    delete(member, directId, directMessageId, 403)
        .body("code", equalTo("MESSAGE_DELETE_FORBIDDEN"));

    String groupId =
        createGroup(owner, "Delete Policy Group", List.of(member.userId(), ordinary.userId()));
    String groupMessageId =
        send(member, groupId, UUID.randomUUID().toString(), "group sender body", 201)
            .extract()
            .jsonPath()
            .getString("messageId");
    delete(ordinary, groupId, groupMessageId, 403)
        .body("code", equalTo("MESSAGE_DELETE_FORBIDDEN"));
  }

  @Test
  void validationShouldRejectInvalidBodiesIdentifiersAndPagination() {
    Account owner = bootstrapAdmin("Validation Owner");
    String conversationId = createGroup(owner, "Validation Group", List.of());

    send(owner, conversationId, UUID.randomUUID().toString(), " \t\n", 400)
        .body("code", equalTo("MESSAGE_VALIDATION_FAILED"));
    send(owner, conversationId, UUID.randomUUID().toString(), "x".repeat(4001), 400)
        .body("code", equalTo("MESSAGE_VALIDATION_FAILED"));
    send(owner, conversationId, "not-a-uuid", "body", 400)
        .body("code", equalTo("MESSAGE_VALIDATION_FAILED"));

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(owner.token()))
        .body(
            Map.of(
                "clientMessageId",
                UUID.randomUUID().toString(),
                "body",
                "known",
                "senderId",
                owner.userId()))
        .when()
        .post("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(400);

    assertInvalidPage(owner, conversationId, "not-a-number", "50");
    assertInvalidPage(owner, conversationId, "-1", "50");
    assertInvalidPage(owner, conversationId, "0", "0");
    assertInvalidPage(owner, conversationId, "0", "201");
  }

  @Test
  void transportLimitShouldAcceptTheLargestValidEscapedMessageBody() {
    Account owner = bootstrapAdmin("Escaped Body Owner");
    String conversationId = createGroup(owner, "Escaped Body Group", List.of());
    String clientMessageId = UUID.randomUUID().toString();
    String request =
        "{\"clientMessageId\":\""
            + clientMessageId
            + "\",\"body\":\""
            + "\\u0061".repeat(4000)
            + "\"}";

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(owner.token()))
        .body(request)
        .when()
        .post("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(201)
        .body("body", equalTo("a".repeat(4000)));
  }

  @Test
  void membershipRemovalShouldWaitForAnExistingMessageAuthorizationLock() throws Exception {
    Account owner = bootstrapAdmin("Removal Lock Owner");
    Account member = inviteMember(owner, "Removal Lock Member");
    String conversationId = createGroup(owner, "Removal Lock Group", List.of(member.userId()));
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var lockFuture =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          assertTrue(
                              messageRepository
                                  .findActiveAccess(
                                      conversationId(conversationId), userId(member.userId()), true)
                                  .isPresent());
                          locked.countDown();
                          await(release, "release membership authorization lock");
                        });
                return null;
              });
      assertTrue(locked.await(3, TimeUnit.SECONDS));

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
        assertThrows(TimeoutException.class, () -> removalFuture.get(250, TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }

      lockFuture.get(3, TimeUnit.SECONDS);
      assertEquals(204, removalFuture.get(3, TimeUnit.SECONDS));
    }

    send(member, conversationId, UUID.randomUUID().toString(), "post-removal", 404)
        .body("code", equalTo("MESSAGE_ACCESS_DENIED"));
  }

  @Test
  void roleDemotionShouldWaitForAnAdministrativeDeleteThatAlreadyHoldsItsLocks() throws Exception {
    Account owner = bootstrapAdmin("Demotion Lock Owner");
    Account administrator = inviteMember(owner, "Demotion Lock Administrator");
    Account sender = inviteMember(owner, "Demotion Lock Sender");
    String conversationId =
        createGroup(owner, "Demotion Lock Group", List.of(administrator.userId(), sender.userId()));
    changeRole(owner, conversationId, administrator.userId(), "ADMIN", 204);
    String messageId =
        send(sender, conversationId, UUID.randomUUID().toString(), "moderated body", 201)
            .extract()
            .jsonPath()
            .getString("messageId");
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var deleteFuture =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          var access =
                              messageRepository
                                  .findActiveAccess(
                                      conversationId(conversationId),
                                      userId(administrator.userId()),
                                      true)
                                  .orElseThrow();
                          assertEquals(ConversationRole.ADMIN, access.role());
                          assertTrue(
                              messageRepository
                                  .findById(
                                      conversationId(conversationId), messageId(messageId), true)
                                  .isPresent());
                          locked.countDown();
                          await(release, "release administrative delete locks");
                          assertTrue(
                              messageRepository.softDelete(messageId(messageId), Instant.now()));
                        });
                return null;
              });
      assertTrue(locked.await(3, TimeUnit.SECONDS));

      var demotionFuture =
          executor.submit(
              () ->
                  changeRole(owner, conversationId, administrator.userId(), "MEMBER", 204)
                      .extract()
                      .statusCode());
      try {
        assertThrows(TimeoutException.class, () -> demotionFuture.get(250, TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }

      deleteFuture.get(3, TimeUnit.SECONDS);
      assertEquals(204, demotionFuture.get(3, TimeUnit.SECONDS));
    }

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(200)
        .body("items[0].messageId", equalTo(messageId))
        .body("items[0].body", equalTo(null))
        .body("items[0].deletedAt", notNullValue());

    String laterMessageId =
        send(sender, conversationId, UUID.randomUUID().toString(), "later body", 201)
            .extract()
            .jsonPath()
            .getString("messageId");
    delete(administrator, conversationId, laterMessageId, 403)
        .body("code", equalTo("MESSAGE_DELETE_FORBIDDEN"));
  }

  @Test
  void concurrentDistinctAndDuplicateSendsShouldKeepOneContiguousSequenceSpace() throws Exception {
    Account owner = bootstrapAdmin("Concurrent Message Owner");
    String conversationId = createGroup(owner, "Concurrent Message Group", List.of());
    String duplicateKey = UUID.randomUUID().toString();

    List<SendCall> calls =
        List.of(
            new SendCall(duplicateKey, "duplicate winner body"),
            new SendCall(duplicateKey, "duplicate retry body"),
            new SendCall(UUID.randomUUID().toString(), "distinct body one"),
            new SendCall(UUID.randomUUID().toString(), "distinct body two"));
    CountDownLatch ready = new CountDownLatch(calls.size());
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(calls.size())) {
      var futures =
          calls.stream()
              .map(
                  call ->
                      executor.submit(concurrentSend(ready, start, owner, conversationId, call)))
              .toList();
      assertTrue(ready.await(3, TimeUnit.SECONDS));
      start.countDown();
      List<SendOutcome> outcomes =
          futures.stream()
              .map(
                  future -> {
                    try {
                      return future.get(10, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                      throw new IllegalStateException(exception);
                    }
                  })
              .toList();

      assertEquals(3, outcomes.stream().map(SendOutcome::messageId).distinct().count());
      assertEquals(3, outcomes.stream().map(SendOutcome::sequence).distinct().count());
      assertTrue(outcomes.stream().allMatch(result -> Set.of(200, 201).contains(result.status())));
    }

    var history =
        given()
            .header("Authorization", bearer(owner.token()))
            .when()
            .get("/api/v1/conversations/{conversationId}/messages", conversationId)
            .then()
            .statusCode(200)
            .body("items", hasSize(3))
            .extract()
            .jsonPath();
    Set<Integer> sequences =
        history.getList("items.sequenceNumber", Integer.class).stream().collect(Collectors.toSet());
    assertEquals(Set.of(1, 2, 3), sequences);
  }

  private static Callable<SendOutcome> concurrentSend(
      CountDownLatch ready,
      CountDownLatch start,
      Account actor,
      String conversationId,
      SendCall call) {
    return () -> {
      ready.countDown();
      if (!start.await(3, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for message send race");
      }
      var response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", bearer(actor.token()))
              .body(Map.of("clientMessageId", call.clientMessageId(), "body", call.body()))
              .when()
              .post("/api/v1/conversations/{conversationId}/messages", conversationId);
      return new SendOutcome(
          response.statusCode(),
          response.jsonPath().getString("messageId"),
          response.jsonPath().getInt("sequenceNumber"));
    };
  }

  private static ValidatableResponse send(
      Account actor,
      String conversationId,
      String clientMessageId,
      String body,
      int expectedStatus) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("clientMessageId", clientMessageId, "body", body))
        .when()
        .post("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse edit(
      Account actor, String conversationId, String messageId, String body, int expectedStatus) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("body", body))
        .when()
        .put(
            "/api/v1/conversations/{conversationId}/messages/{messageId}",
            conversationId,
            messageId)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse delete(
      Account actor, String conversationId, String messageId, int expectedStatus) {
    return given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .delete(
            "/api/v1/conversations/{conversationId}/messages/{messageId}",
            conversationId,
            messageId)
        .then()
        .statusCode(expectedStatus);
  }

  private static ValidatableResponse changeRole(
      Account actor, String conversationId, String targetUserId, String role, int expectedStatus) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("role", role))
        .when()
        .put(
            "/api/v1/conversations/{conversationId}/members/{userId}/role",
            conversationId,
            targetUserId)
        .then()
        .statusCode(expectedStatus);
  }

  private static void assertInvalidPage(
      Account actor, String conversationId, String afterSequence, String limit) {
    given()
        .header("Authorization", bearer(actor.token()))
        .queryParam("afterSequence", afterSequence)
        .queryParam("limit", limit)
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(400)
        .body("code", equalTo("MESSAGE_VALIDATION_FAILED"));
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
    return new Account(userId, username, login(username, ADMIN_PASSWORD));
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
    return new Account(userId, username, login(username, MEMBER_PASSWORD));
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

  private static String latestAuditMetadata(String eventType) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement =
            connection.prepareStatement(
                "SELECT TOP 1 metadata FROM [audit].[http_audit_event] "
                    + "WHERE event_type = ? ORDER BY occurred_at DESC")) {
      statement.setString(1, eventType);
      try (var result = statement.executeQuery()) {
        assertTrue(result.next(), "Expected a durable message audit record");
        return result.getString(1);
      }
    }
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static ConversationId conversationId(String value) {
    return new ConversationId(UUID.fromString(value));
  }

  private static UserId userId(String value) {
    return new UserId(UUID.fromString(value));
  }

  private static MessageId messageId(String value) {
    return new MessageId(UUID.fromString(value));
  }

  private static void await(CountDownLatch latch, String description) {
    try {
      if (!latch.await(3, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to " + description);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting to " + description, exception);
    }
  }

  private record Account(String userId, String username, String token) {}

  private record SendCall(String clientMessageId, String body) {}

  private record SendOutcome(int status, String messageId, int sequence) {}
}
