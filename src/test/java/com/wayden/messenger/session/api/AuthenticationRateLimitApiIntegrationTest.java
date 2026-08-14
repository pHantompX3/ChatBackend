package com.wayden.messenger.session.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
@TestProfile(AuthenticationRateLimitApiIntegrationTest.RateLimitProfile.class)
final class AuthenticationRateLimitApiIntegrationTest {

  @BeforeEach
  void resetRateLimits() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                IdentitySqlServerTestResource.jdbcUrl("wl_chat"),
                "sa",
                IdentitySqlServerTestResource.saPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM [audit].[http_audit_event]");
      statement.executeUpdate("DELETE FROM [identity].[authentication_rate_limit]");
      statement.executeUpdate("DELETE FROM [identity].[session]");
      statement.executeUpdate("DELETE FROM [identity].[invitation]");
      statement.executeUpdate("DELETE FROM [identity].[user_account]");
    }
  }

  @Test
  void accountWindowShouldRejectAtTheBoundaryWithoutDisclosingAccountExistence() {
    Map<String, String> credentials =
        Map.of("username", "nonexistent-account", "password", "wrong-password");

    for (int attempt = 0; attempt < 2; attempt++) {
      given()
          .contentType(ContentType.JSON)
          .body(credentials)
          .when()
          .post("/api/v1/sessions")
          .then()
          .statusCode(401)
          .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    given()
        .contentType(ContentType.JSON)
        .body(credentials)
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(429)
        .contentType(startsWith("application/problem+json"))
        .header("Retry-After", matchesPattern("[1-9][0-9]*"))
        .body("code", equalTo("AUTHENTICATION_RATE_LIMITED"))
        .body("detail", equalTo("Too many authentication attempts; try again later"));
  }

  @Test
  void sourceWindowShouldAggregateAttemptsAcrossDifferentAccounts() {
    for (int attempt = 0; attempt < 3; attempt++) {
      given()
          .contentType(ContentType.JSON)
          .body(Map.of("username", "source-account-" + attempt, "password", "wrong-password"))
          .when()
          .post("/api/v1/sessions")
          .then()
          .statusCode(401)
          .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("username", "source-account-rejected", "password", "wrong-password"))
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(429)
        .body("code", equalTo("AUTHENTICATION_RATE_LIMITED"));
  }

  @Test
  void successfulAttemptsShouldConsumeAccountCapacityWithoutResettingTheWindow() {
    Map<String, String> credentials =
        Map.of("username", "Rate Limit Admin", "password", "AdminPassw0rd!");
    given()
        .contentType(ContentType.JSON)
        .body(credentials)
        .when()
        .post("/api/v1/bootstrap/admin")
        .then()
        .statusCode(200);

    for (int attempt = 0; attempt < 2; attempt++) {
      given()
          .contentType(ContentType.JSON)
          .body(credentials)
          .when()
          .post("/api/v1/sessions")
          .then()
          .statusCode(200);
    }

    given()
        .contentType(ContentType.JSON)
        .body(credentials)
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(429)
        .body("code", equalTo("AUTHENTICATION_RATE_LIMITED"));
  }

  public static final class RateLimitProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "chat.auth.rate-limit.enabled", "true",
          "chat.auth.rate-limit.account-limit", "2",
          "chat.auth.rate-limit.account-window", "PT1M",
          "chat.auth.rate-limit.source-limit", "3",
          "chat.auth.rate-limit.source-window", "PT1M");
    }
  }
}
