package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.NormalizedUsername;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
  boolean existsAnyUser();

  Optional<User> findById(UserId userId);

  Optional<User> findByNormalizedUsername(NormalizedUsername normalizedUsername);

  List<User> searchActiveByUsernamePrefix(
      NormalizedUsername prefix,
      NormalizedUsername afterUsername,
      UserId afterUserId,
      UserId excludedUserId,
      int limit);

  User save(User user);

  User saveFirstAdminIfAbsent(User user);
}
