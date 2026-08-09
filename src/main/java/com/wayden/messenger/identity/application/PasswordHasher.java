package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.PasswordHash;

public interface PasswordHasher {
  PasswordHash hash(String rawPassword);

  boolean verify(String rawPassword, PasswordHash passwordHash);
}
