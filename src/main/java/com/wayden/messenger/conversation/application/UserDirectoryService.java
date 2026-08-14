package com.wayden.messenger.conversation.application;

import com.wayden.messenger.common.api.PaginationPolicy;
import com.wayden.messenger.conversation.application.ConversationCursorCodec.UserCursor;
import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class UserDirectoryService {

  private final UserRepository userRepository;
  private final ConversationCursorCodec cursorCodec;

  @Inject
  public UserDirectoryService(UserRepository userRepository, ConversationCursorCodec cursorCodec) {
    this.userRepository = userRepository;
    this.cursorCodec = cursorCodec;
  }

  public UserPage search(UserId actorUserId, String rawQuery, String rawCursor, Integer rawLimit) {
    if (actorUserId == null) {
      throw new ConversationExceptions.ValidationException("Authenticated user is required");
    }
    if (rawQuery == null || rawQuery.trim().length() < 2 || rawQuery.trim().length() > 64) {
      throw new ConversationExceptions.UserSearchValidationException(
          "User search query must contain between 2 and 64 characters");
    }

    NormalizedUsername query = NormalizedUsername.fromRaw(rawQuery);
    UserCursor cursor;
    int limit;
    try {
      PaginationPolicy.requireValidCursorLength(rawCursor);
      cursor = cursorCodec.decodeUser(rawCursor, query);
      limit = PaginationPolicy.resolveLimit(rawLimit, 20, 50);
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.UserSearchValidationException(exception.getMessage());
    }
    List<UserSummary> items =
        userRepository
            .searchActiveByUsernamePrefix(
                query,
                cursor == null ? null : cursor.normalizedUsername(),
                cursor == null ? null : cursor.userId(),
                actorUserId,
                limit + 1)
            .stream()
            .map(user -> new UserSummary(user.id(), user.username(), user.normalizedUsername()))
            .toList();

    boolean hasNext = items.size() > limit;
    List<UserSummary> pageItems = hasNext ? items.subList(0, limit) : items;
    String nextCursor = null;
    if (hasNext) {
      UserSummary last = pageItems.get(pageItems.size() - 1);
      nextCursor =
          cursorCodec.encodeUser(new UserCursor(query, last.normalizedUsername(), last.userId()));
    }
    return new UserPage(pageItems, nextCursor);
  }

  public record UserSummary(
      UserId userId, String username, NormalizedUsername normalizedUsername) {}

  public record UserPage(List<UserSummary> items, String nextCursor) {

    public UserPage {
      items = List.copyOf(items);
    }
  }
}
