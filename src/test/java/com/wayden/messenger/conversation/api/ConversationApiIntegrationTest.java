package com.wayden.messenger.conversation.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class ConversationApiIntegrationTest {

  private static final String ADMIN_PASSWORD = "AdminPassw0rd!";
  private static final String MEMBER_PASSWORD = "MemberPassw0rd!";

  @BeforeEach
  void resetTables() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
      statement.executeUpdate("DELETE FROM [messaging].[direct_conversation_pair]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation_member]");
      statement.executeUpdate("DELETE FROM [messaging].[conversation]");
      statement.executeUpdate("DELETE FROM [identity].[session]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    }
  }

  @Test
  void authenticatedUserSearchShouldReturnOnlyMatchingActivePublicIdentity() {
    Account admin = bootstrapAdmin("Directory Admin");
    Account alex = inviteMember(admin, "Alex Member");
    inviteMember(admin, "Other Member");

    given()
        .header("Authorization", bearer(admin.token()))
        .queryParam("query", "al")
        .when()
        .get("/api/v1/users")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].userId", equalTo(alex.userId()))
        .body("items[0].username", equalTo("Alex Member"))
        .body("items[0].systemRole", equalTo(null));

    given()
        .header("Authorization", bearer(admin.token()))
        .queryParam("query", "a")
        .when()
        .get("/api/v1/users")
        .then()
        .statusCode(400)
        .body("code", equalTo("USER_SEARCH_VALIDATION_FAILED"));
  }

  @Test
  void directCreationShouldBeIdempotentForUnorderedPair() {
    Account admin = bootstrapAdmin("Direct Admin");
    Account member = inviteMember(admin, "Direct Member");

    String conversationId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", bearer(admin.token()))
            .body(Map.of("targetUserId", member.userId()))
            .when()
            .post("/api/v1/conversations/direct")
            .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("role", equalTo("MEMBER"))
            .extract()
            .jsonPath()
            .getString("conversationId");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(member.token()))
        .body(Map.of("targetUserId", admin.userId()))
        .when()
        .post("/api/v1/conversations/direct")
        .then()
        .statusCode(200)
        .body("conversationId", equalTo(conversationId));
  }

  @RepeatedTest(5)
  void concurrentDirectCreationShouldPersistOneConversation() throws Exception {
    Account admin = bootstrapAdmin("Concurrent Admin");
    Account member = inviteMember(admin, "Concurrent Member");

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<String> fromAdmin = concurrentCreate(ready, start, admin, member.userId());
    Callable<String> fromMember = concurrentCreate(ready, start, member, admin.userId());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var adminResult = executor.submit(fromAdmin);
      var memberResult = executor.submit(fromMember);
      org.junit.jupiter.api.Assertions.assertTrue(ready.await(2, TimeUnit.SECONDS));
      start.countDown();
      assertEqualConversationIds(adminResult.get(), memberResult.get());
    }
  }

  @Test
  void groupRolesRemovalAndOwnershipShouldFollowPolicy() {
    Account owner = bootstrapAdmin("Group Owner");
    Account firstMember = inviteMember(owner, "Group First");
    Account secondMember = inviteMember(owner, "Group Second");

    String conversationId = createGroup(owner, "Policy Group", List.of(firstMember.userId()));

    given()
        .header("Authorization", bearer(firstMember.token()))
        .when()
        .put(
            "/api/v1/conversations/{conversationId}/members/{userId}",
            conversationId,
            secondMember.userId())
        .then()
        .statusCode(403)
        .body("code", equalTo("CONVERSATION_ROLE_FORBIDDEN"));

    addMember(owner, conversationId, secondMember.userId());
    changeRole(owner, conversationId, firstMember.userId(), "ADMIN");

    given()
        .header("Authorization", bearer(firstMember.token()))
        .when()
        .delete(
            "/api/v1/conversations/{conversationId}/members/{userId}",
            conversationId,
            secondMember.userId())
        .then()
        .statusCode(204);

    given()
        .header("Authorization", bearer(secondMember.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}", conversationId)
        .then()
        .statusCode(404)
        .body("code", equalTo("CONVERSATION_ACCESS_DENIED"));

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .post(
            "/api/v1/conversations/{conversationId}/members/{userId}/transfer-ownership",
            conversationId,
            firstMember.userId())
        .then()
        .statusCode(204);

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .post("/api/v1/conversations/{conversationId}/leave", conversationId)
        .then()
        .statusCode(204);

    given()
        .header("Authorization", bearer(firstMember.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}", conversationId)
        .then()
        .statusCode(200)
        .body("role", equalTo("OWNER"));
  }

  @Test
  void nonMemberShouldNotEnumerateOrInspectConversation() {
    Account owner = bootstrapAdmin("Private Owner");
    Account member = inviteMember(owner, "Private Member");
    Account outsider = inviteMember(owner, "Private Outsider");
    String conversationId = createGroup(owner, "Private Group", List.of(member.userId()));

    given()
        .header("Authorization", bearer(outsider.token()))
        .when()
        .get("/api/v1/conversations")
        .then()
        .statusCode(200)
        .body("items", empty());

    given()
        .header("Authorization", bearer(outsider.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}", conversationId)
        .then()
        .statusCode(404)
        .body("code", equalTo("CONVERSATION_ACCESS_DENIED"));
  }

  @Test
  void systemAdminShouldNotBypassMembershipAndOwnerMustTransferBeforeLeaving() {
    Account systemAdmin = bootstrapAdmin("Platform Admin Outsider");
    Account owner = inviteMember(systemAdmin, "Member Group Owner");
    Account member = inviteMember(systemAdmin, "Member Group Participant");
    String conversationId = createGroup(owner, "Member Owned Group", List.of(member.userId()));

    given()
        .header("Authorization", bearer(systemAdmin.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}", conversationId)
        .then()
        .statusCode(404)
        .body("code", equalTo("CONVERSATION_ACCESS_DENIED"));

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .post("/api/v1/conversations/{conversationId}/leave", conversationId)
        .then()
        .statusCode(409)
        .body("code", equalTo("CONVERSATION_OWNERSHIP_REQUIRED"));
  }

  @Test
  void conversationListShouldUseStableSeekCursor() {
    Account owner = bootstrapAdmin("Paging Owner");
    createGroup(owner, "First Page Group", List.of());
    createGroup(owner, "Second Page Group", List.of());

    var firstPage =
        given()
            .header("Authorization", bearer(owner.token()))
            .queryParam("limit", 1)
            .when()
            .get("/api/v1/conversations")
            .then()
            .statusCode(200)
            .body("items", hasSize(1))
            .body("nextCursor", notNullValue())
            .extract()
            .jsonPath();

    String firstId = firstPage.getString("items[0].conversationId");
    String cursor = firstPage.getString("nextCursor");
    String secondId =
        given()
            .header("Authorization", bearer(owner.token()))
            .queryParam("limit", 1)
            .queryParam("cursor", cursor)
            .when()
            .get("/api/v1/conversations")
            .then()
            .statusCode(200)
            .body("items", hasSize(1))
            .extract()
            .jsonPath()
            .getString("items[0].conversationId");

    org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);

    given()
        .header("Authorization", bearer(owner.token()))
        .queryParam("cursor", "not-a-cursor")
        .when()
        .get("/api/v1/conversations")
        .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_CURSOR"));
  }

  @Test
  void conversationMessageRouteShouldRemainReachableAndAuthenticated() {
    Account owner = bootstrapAdmin("Message Route Owner");
    String conversationId = createGroup(owner, "Message Route Group", List.of());

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(501);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(owner.token()))
        .when()
        .post("/api/v1/sessions/logout")
        .then()
        .statusCode(204);

    given()
        .header("Authorization", bearer(owner.token()))
        .when()
        .get("/api/v1/conversations/{conversationId}/messages", conversationId)
        .then()
        .statusCode(401);
  }

  private static String createDirect(Account actor, String targetUserId) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("targetUserId", targetUserId))
        .when()
        .post("/api/v1/conversations/direct")
        .then()
        .statusCode(org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(201)))
        .extract()
        .jsonPath()
        .getString("conversationId");
  }

  private static Callable<String> concurrentCreate(
      CountDownLatch ready, CountDownLatch start, Account actor, String targetUserId) {
    return () -> {
      ready.countDown();
      if (!start.await(2, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to start direct conversation race");
      }
      return createDirect(actor, targetUserId);
    };
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

  private static void addMember(Account actor, String conversationId, String targetUserId) {
    given()
        .header("Authorization", bearer(actor.token()))
        .when()
        .put(
            "/api/v1/conversations/{conversationId}/members/{userId}", conversationId, targetUserId)
        .then()
        .statusCode(204);
  }

  private static void changeRole(
      Account actor, String conversationId, String targetUserId, String role) {
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", bearer(actor.token()))
        .body(Map.of("role", role))
        .when()
        .put(
            "/api/v1/conversations/{conversationId}/members/{userId}/role",
            conversationId,
            targetUserId)
        .then()
        .statusCode(204);
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static void assertEqualConversationIds(String first, String second) {
    org.junit.jupiter.api.Assertions.assertEquals(first, second);
  }

  private record Account(String userId, String username, String token) {}
}
