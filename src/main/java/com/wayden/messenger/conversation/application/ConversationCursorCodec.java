package com.wayden.messenger.conversation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ConversationCursorCodec {

  private static final Set<String> CONVERSATION_FIELDS = Set.of("v", "updatedAt", "conversationId");
  private static final Set<String> USER_FIELDS =
      Set.of("v", "query", "normalizedUsername", "userId");

  private final ObjectMapper objectMapper;

  @Inject
  public ConversationCursorCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encodeConversation(ConversationCursor cursor) {
    return encode(
        new ConversationCursorPayload(
            1, cursor.updatedAt().toString(), cursor.conversationId().value().toString()));
  }

  public ConversationCursor decodeConversation(String rawCursor) {
    if (rawCursor == null || rawCursor.isBlank()) {
      return null;
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(rawCursor);
      JsonNode node = objectMapper.readTree(decoded);
      requireFields(node, CONVERSATION_FIELDS);
      if (node.get("v").asInt() != 1) {
        throw new ConversationExceptions.InvalidCursorException();
      }
      return new ConversationCursor(
          Instant.parse(node.get("updatedAt").asText()),
          new ConversationId(UUID.fromString(node.get("conversationId").asText())));
    } catch (ConversationExceptions.InvalidCursorException exception) {
      throw exception;
    } catch (RuntimeException | java.io.IOException exception) {
      throw new ConversationExceptions.InvalidCursorException(exception);
    }
  }

  public String encodeUser(UserCursor cursor) {
    return encode(
        new UserCursorPayload(
            1,
            cursor.query().value(),
            cursor.normalizedUsername().value(),
            cursor.userId().value().toString()));
  }

  public UserCursor decodeUser(String rawCursor, NormalizedUsername query) {
    if (rawCursor == null || rawCursor.isBlank()) {
      return null;
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(rawCursor);
      JsonNode node = objectMapper.readTree(decoded);
      requireFields(node, USER_FIELDS);
      if (node.get("v").asInt() != 1 || !query.value().equals(node.get("query").asText())) {
        throw new ConversationExceptions.InvalidCursorException();
      }
      return new UserCursor(
          query,
          new NormalizedUsername(node.get("normalizedUsername").asText()),
          new UserId(UUID.fromString(node.get("userId").asText())));
    } catch (ConversationExceptions.InvalidCursorException exception) {
      throw exception;
    } catch (RuntimeException | java.io.IOException exception) {
      throw new ConversationExceptions.InvalidCursorException(exception);
    }
  }

  private String encode(Object payload) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Failed to encode cursor", exception);
    }
  }

  private static void requireFields(JsonNode node, Set<String> expectedFields) {
    if (node == null || !node.isObject()) {
      throw new ConversationExceptions.InvalidCursorException();
    }
    java.util.Set<String> actualFields = new java.util.HashSet<>();
    node.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(expectedFields)) {
      throw new ConversationExceptions.InvalidCursorException();
    }
  }

  public record ConversationCursor(Instant updatedAt, ConversationId conversationId) {}

  public record UserCursor(
      NormalizedUsername query, NormalizedUsername normalizedUsername, UserId userId) {}

  private record ConversationCursorPayload(int v, String updatedAt, String conversationId) {}

  private record UserCursorPayload(int v, String query, String normalizedUsername, String userId) {}
}
