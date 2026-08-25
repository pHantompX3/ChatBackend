package com.wayden.messenger.realtime.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wayden.messenger.bootstrap.IdentitySqlServerTestResource;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.message.application.MessageEvents;
import com.wayden.messenger.message.domain.ClientMessageId;
import com.wayden.messenger.message.domain.MessageId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketClientConnection;
import io.restassured.http.ContentType;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.net.URI;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(IdentitySqlServerTestResource.class)
final class WebSocketTransportIntegrationTest {

  private static final String PASSWORD = "AdminPassw0rd!";

  @TestHTTPResource("/")
  URI baseUri;

  @Inject Event<MessageEvents.MessageCreatedEvent> messageCreatedEvent;

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
  void shouldAuthenticatePingAndCloseEverySocketWhenSessionIsRevoked() throws Exception {
    Account account = bootstrapAndLogin();
    String conversationId = createGroup(account.token());
    SocketClient first = connect(account.token());
    SocketClient second = connect(account.token());

    first.connection().sendTextAndAwait("{\"action\":\"ping\"}");

    assertEquals("{\"type\":\"pong\"}", first.frames().poll(3, TimeUnit.SECONDS));

    QuarkusTransaction.requiringNew()
        .run(() -> messageCreatedEvent.fire(event(conversationId, account.userId(), "committed")));
    assertNotNull(first.frames().poll(3, TimeUnit.SECONDS));
    assertNotNull(second.frames().poll(3, TimeUnit.SECONDS));

    assertThrows(
        IllegalStateException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(
                    () -> {
                      messageCreatedEvent.fire(event(conversationId, account.userId(), "rollback"));
                      throw new IllegalStateException("force rollback");
                    }));
    assertNull(first.frames().poll(300, TimeUnit.MILLISECONDS));
    assertNull(second.frames().poll(300, TimeUnit.MILLISECONDS));

    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + account.token())
        .when()
        .post("/api/v1/sessions/logout")
        .then()
        .statusCode(204);

    CloseReason firstClose = first.closes().poll(3, TimeUnit.SECONDS);
    CloseReason secondClose = second.closes().poll(3, TimeUnit.SECONDS);
    assertNotNull(firstClose);
    assertNotNull(secondClose);
    assertEquals(4401, firstClose.getCode());
    assertEquals(4401, secondClose.getCode());
  }

  private SocketClient connect(String token) {
    var frames = new LinkedBlockingQueue<String>();
    var closes = new LinkedBlockingQueue<CloseReason>();
    WebSocketClientConnection connection =
        BasicWebSocketConnector.create()
            .baseUri(baseUri)
            .path("/api/v1/ws")
            .addHeader("Authorization", "bearer " + token + "   ")
            .onTextMessage((ignored, message) -> frames.add(message))
            .onClose((ignored, reason) -> closes.add(reason))
            .connectAndAwait();
    return new SocketClient(connection, frames, closes);
  }

  private static Account bootstrapAndLogin() {
    String username = "WebSocket Transport Admin";
    String userId =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", PASSWORD))
            .when()
            .post("/api/v1/bootstrap/admin")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("userId");
    String token =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", PASSWORD))
            .when()
            .post("/api/v1/sessions")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("token");
    return new Account(userId, token);
  }

  private static String createGroup(String token) {
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + token)
        .body(Map.of("title", "WebSocket transaction boundary", "initialMemberIds", List.of()))
        .when()
        .post("/api/v1/conversations/groups")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getString("conversationId");
  }

  private static MessageEvents.MessageCreatedEvent event(
      String conversationId, String userId, String body) {
    return new MessageEvents.MessageCreatedEvent(
        new ConversationId(UUID.fromString(conversationId)),
        new MessageId(UUID.randomUUID()),
        1,
        new UserId(UUID.fromString(userId)),
        new ClientMessageId(UUID.randomUUID()),
        body,
        Instant.now());
  }

  private record SocketClient(
      WebSocketClientConnection connection,
      LinkedBlockingQueue<String> frames,
      LinkedBlockingQueue<CloseReason> closes) {}

  private record Account(String userId, String token) {}
}
