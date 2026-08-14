package com.wayden.messenger.common.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class ApiHardeningIntegrationTest {

  @Test
  void malformedJsonUsesTheCanonicalProblemContractAndCorrelationHeaders() {
    given()
        .contentType(ContentType.JSON)
        .header("X-Request-Id", "caller-controlled")
        .body("{")
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .header("X-Request-Id", notNullValue())
        .header("X-Request-Id", org.hamcrest.Matchers.not("caller-controlled"))
        .body("type", equalTo("urn:wl-chat:problem:malformed-json"))
        .body("code", equalTo("MALFORMED_JSON"))
        .body("requestId", notNullValue());
  }

  @Test
  void frameworkMethodRejectionUsesTheCanonicalProblemContract() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/api/v1/ping")
        .then()
        .statusCode(405)
        .contentType("application/problem+json")
        .body("type", equalTo("urn:wl-chat:problem:method-not-allowed"))
        .body("code", equalTo("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unsupportedMediaTypeUsesTheCanonicalProblemContract() {
    given()
        .contentType(ContentType.TEXT)
        .body("not-json")
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(415)
        .contentType("application/problem+json")
        .body("type", equalTo("urn:wl-chat:problem:unsupported-media-type"))
        .body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void oversizedBodyUsesTheCanonicalProblemContract() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"username\":\"" + "a".repeat(33_000) + "\"}")
        .when()
        .post("/api/v1/sessions")
        .then()
        .statusCode(413)
        .contentType("application/problem+json")
        .header("X-Request-Id", notNullValue())
        .body("type", startsWith("urn:wl-chat:problem:"))
        .body("code", equalTo("PAYLOAD_TOO_LARGE"));
  }
}
