package com.wayden.messenger.identity.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class IdentityApiIntegrationTest {

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
  void bootstrapAdminShouldSucceedThenReturnConflictOnSecondAttempt() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "Admin Root", "password", "AdminPassw0rd!"))
        .when()
        .post("/api/v1/bootstrap/admin")
        .then()
        .statusCode(200)
        .body("userId", notNullValue())
        .body("username", equalTo("Admin Root"));

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "Other Admin", "password", "AdminPassw0rd!"))
        .when()
        .post("/api/v1/bootstrap/admin")
        .then()
        .statusCode(409)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("BOOTSTRAP_ALREADY_COMPLETED"));
  }

  @Test
  void redeemInvitationShouldReturnAlreadyRedeemedOnSecondRedeem() {
    String adminUserId = bootstrapAdmin("Admin Owner");
    String invitationToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", invitationToken,
                "username", "member-alpha",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(200)
        .body("userId", notNullValue())
        .body("username", equalTo("member-alpha"));

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", invitationToken,
                "username", "member-beta",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(422)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_ALREADY_REDEEMED"));
  }

  @Test
  void revokedInvitationShouldNotBeRedeemable() {
    String adminUserId = bootstrapAdmin("Admin Owner Two");
    JsonPath createdInvitation =
        given()
            .contentType(ContentType.JSON)
            .body(
                Map.of(
                    "actorUserId",
                    adminUserId,
                    "expiresAt",
                    Instant.now().plus(1, ChronoUnit.DAYS).toString()))
            .when()
            .post("/api/v1/invitations")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();

    String invitationId = createdInvitation.getString("invitationId");
    String invitationToken = createdInvitation.getString("invitationToken");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", adminUserId))
        .when()
        .post("/api/v1/invitations/" + invitationId + "/revoke")
        .then()
        .statusCode(204);

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", invitationToken,
                "username", "member-gamma",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(422)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_REVOKED"));
  }

  @Test
  void revokeUnknownInvitationShouldReturnNotFound() {
    String adminUserId = bootstrapAdmin("Admin Owner Three");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", adminUserId))
        .when()
        .post("/api/v1/invitations/" + UUID.randomUUID() + "/revoke")
        .then()
        .statusCode(404)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_NOT_FOUND"));
  }

  @Test
  void createInvitationWithUnknownActorShouldReturnForbidden() {
    bootstrapAdmin("Admin Owner Six");

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "actorUserId", UUID.randomUUID().toString(),
                "expiresAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(403)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_ACTOR_FORBIDDEN"));
  }

  @Test
  void revokeInvitationWithUnknownActorShouldReturnForbidden() {
    String adminUserId = bootstrapAdmin("Admin Owner Seven");
    JsonPath createdInvitation =
        createInvitationResponse(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));
    String invitationId = createdInvitation.getString("invitationId");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", UUID.randomUUID().toString()))
        .when()
        .post("/api/v1/invitations/" + invitationId + "/revoke")
        .then()
        .statusCode(403)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_ACTOR_FORBIDDEN"));
  }

  @Test
  void expiredInvitationShouldReturnGone() {
    String adminUserId = bootstrapAdmin("Admin Owner Four");
    JsonPath createdInvitation =
        createInvitationResponse(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));
    String invitationId = createdInvitation.getString("invitationId");
    String invitationToken = createdInvitation.getString("invitationToken");
    forceInvitationExpired(invitationId);

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", invitationToken,
                "username", "member-expired",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(410)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVITATION_EXPIRED"));
  }

  @Test
  void redeemWithExistingUsernameShouldReturnConflict() {
    String adminUserId = bootstrapAdmin("Admin Owner Five");
    String firstToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));
    String secondToken = createInvitation(adminUserId, Instant.now().plus(1, ChronoUnit.DAYS));

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", firstToken,
                "username", "member-duplicate",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(200);

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "invitationToken", secondToken,
                "username", "member-duplicate",
                "password", "MemberPassw0rd!"))
        .when()
        .post("/api/v1/invitations/redeem")
        .then()
        .statusCode(409)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("DUPLICATE_USERNAME"));
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

  private static String createInvitation(String actorUserId, Instant expiresAt) {
    return createInvitationResponse(actorUserId, expiresAt).getString("invitationToken");
  }

  private static JsonPath createInvitationResponse(String actorUserId, Instant expiresAt) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", actorUserId, "expiresAt", expiresAt.toString()))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath();
  }

  private static void forceInvitationExpired(String invitationId) {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement =
            connection.prepareStatement(
                "UPDATE [identity].[invitation] "
                    + "SET created_at = DATEADD(minute, -10, SYSUTCDATETIME()), "
                    + "expires_at = DATEADD(minute, -5, SYSUTCDATETIME()) "
                    + "WHERE id = ?")) {
      statement.setString(1, invitationId);
      int updatedRows = statement.executeUpdate();
      if (updatedRows != 1) {
        throw new IllegalStateException("Expected to update exactly one invitation row");
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to force invitation expiry in test setup", exception);
    }
  }
}
