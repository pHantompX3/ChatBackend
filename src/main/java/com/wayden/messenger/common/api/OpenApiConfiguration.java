package com.wayden.messenger.common.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@ApplicationPath("/")
@OpenAPIDefinition(
    info =
        @Info(
            title = "WL Chat Backend API",
            version = "0.7.0",
            description =
                "Client-independent HTTP API for identity, conversations, durable messages, and delivery state."),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "opaque")
public final class OpenApiConfiguration extends Application {}
