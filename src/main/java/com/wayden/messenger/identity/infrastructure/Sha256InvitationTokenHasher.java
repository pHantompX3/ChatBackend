package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.InvitationTokenHasher;
import com.wayden.messenger.identity.domain.InvitationTokenHash;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@ApplicationScoped
public class Sha256InvitationTokenHasher implements InvitationTokenHasher {

  @Override
  public InvitationTokenHash hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("Invitation token must not be blank");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] value = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return new InvitationTokenHash(value);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }
}
