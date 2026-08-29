package com.wayden.messenger.common.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class DatabaseTlsPolicyTest {

  @Test
  void hardenedProfileShouldRequireEncryptionAndCertificateValidation() {
    assertDoesNotThrow(
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:sqlserver://sqlserver:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=false"));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:sqlserver://sqlserver:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true"));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:sqlserver://sqlserver:1433;databaseName=wl_chat;trustServerCertificate=false"));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:sqlserver://sqlserver:1433;encrypt=true;trustServerCertificate=false;encrypt=false"));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:sqlserver://sqlserver:1433;encrypt=trueish;trustServerCertificate=false"));
    assertThrows(
        IllegalStateException.class,
        () ->
            DatabaseTlsPolicy.validate(
                true,
                "jdbc:postgresql://sqlserver:1433;encrypt=true;trustServerCertificate=false"));
  }

  @Test
  void localProfileShouldRetainExplicitDevelopmentCompatibility() {
    assertDoesNotThrow(
        () ->
            DatabaseTlsPolicy.validate(
                false,
                "jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true"));
  }
}
