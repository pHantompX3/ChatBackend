package com.wayden.messenger.bootstrap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HealthEndpointTest {

  @Test
  void livenessShouldReportUp() {
    given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  void readinessShouldReportUp() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
  }
}
