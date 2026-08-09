package com.wayden.messenger.identity.application;

import com.wayden.messenger.identity.domain.InvitationTokenHash;

public interface InvitationTokenHasher {
  InvitationTokenHash hash(String rawToken);
}
