package com.wayden.messenger.identity.infrastructure;

import com.wayden.messenger.identity.application.InvitationTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class SecureInvitationTokenGenerator implements InvitationTokenGenerator {

  private static final int TOKEN_BYTES = 32;
  private final SecureRandom random = new SecureRandom();

  @Override
  public String generateToken() {
    byte[] value = new byte[TOKEN_BYTES];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
