package com.wayden.messenger.common.api;

import java.net.URI;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ApiProblem", description = "RFC 9457 problem detail with stable application code")
public record ApiProblem(
    URI type,
    String title,
    int status,
    String detail,
    URI instance,
    String code,
    String requestId) {}
