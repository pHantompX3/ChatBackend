package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.PasswordHasher;
import com.wayden.messenger.identity.domain.PasswordHash;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Argon2PasswordHasher implements PasswordHasher {

  private static final int ITERATIONS = 3;
  private static final int MEMORY_KIB = 1 << 15;
  private static final int PARALLELISM = 1;

  @PostConstruct
  void verifyNativeLibraryAvailable() {
    Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
  }

  @Override
  public PasswordHash hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("Password must not be blank");
    }

    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    char[] passwordChars = rawPassword.toCharArray();
    try {
      String encoded = argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, passwordChars);
      return new PasswordHash(encoded);
    } finally {
      argon2.wipeArray(passwordChars);
    }
  }

  @Override
  public boolean verify(String rawPassword, PasswordHash passwordHash) {
    if (rawPassword == null || passwordHash == null) {
      return false;
    }

    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    char[] passwordChars = rawPassword.toCharArray();
    try {
      return argon2.verify(passwordHash.value(), passwordChars);
    } finally {
      argon2.wipeArray(passwordChars);
    }
  }
}
