package com.wayden.messenger.identity.application;

public interface InvitationService {
  CreateInvitationResult createInvitation(CreateInvitationCommand command);

  void revokeInvitation(RevokeInvitationCommand command);

  RedeemInvitationResult redeemInvitation(RedeemInvitationCommand command);
}
