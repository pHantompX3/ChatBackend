package com.wayden.messenger.session.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class SessionApiIntegrationTest {

  @BeforeEach
  void resetIdentityTables() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
      statement.executeUpdate("DELETE FROM [identity].[session]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    }
  }

  @Test
  void loginShouldCreateSessionAndAuthorizeProtectedRoute() {
    String adminUserId = bootstrapAdmin("Session Admin");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("actorUserId", adminUserId, "expiresAt", "2099-01-01T00:00:00Z"))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(401)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("MISSING_TOKEN"));

    var loginResponse =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "Session Admin", "password", "AdminPassw0rd!"))
            .when()
            .post("/api/v1/sessions")
            .then()
            .statusCode(200)
            .body("sessionId", notNullValue())
            .body("token", notNullValue())
            .extract()
            .jsonPath();

    String token = loginResponse.getString("token");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(Map.of("actorUserId", adminUserId, "expiresAt", "2099-01-01T00:00:00Z"))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(200)
        .body("invitationId", notNullValue())
        .body("invitationToken", notNullValue());
  }

  @Test
  void invalidCredentialsShouldReturnUnauthorized() {
    bootstrapAdmin("Session Admin Invalid");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "Session Admin Invalid", "password", "wrong-password"))
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(401)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("INVALID_CREDENTIALS"));
  }

  @Test
  void revokedSessionShouldBeRejectedForProtectedRoute() {
    String adminUserId = bootstrapAdmin("Session Admin Logout");

    String token =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "Session Admin Logout", "password", "AdminPassw0rd!"))
            .when()
            .post("/api/v1/sessions")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("token");

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .when()
        .post("/api/v1/sessions/logout")
        .then()
        .statusCode(204);

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(Map.of("actorUserId", adminUserId, "expiresAt", "2099-01-01T00:00:00Z"))
        .when()
        .post("/api/v1/invitations")
        .then()
        .statusCode(401)
        .contentType(startsWith("application/problem+json"))
        .body("code", equalTo("SESSION_REVOKED"));
  }

  private String bootstrapAdmin(String username) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", "AdminPassw0rd!"))
        .when()
        .post("/api/v1/bootstrap/admin")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getString("userId");
  }
}
