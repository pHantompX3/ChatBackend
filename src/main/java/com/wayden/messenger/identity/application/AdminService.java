package com.wayden.messenger.identity.application;

public interface AdminService {
  BootstrapAdminResult bootstrapFirstAdmin(BootstrapAdminCommand command);
}
