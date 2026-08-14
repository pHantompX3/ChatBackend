package com.wayden.messenger.delivery.api;

import com.fasterxml.jackson.databind.JsonNode;

public record AcknowledgePositionRequest(JsonNode sequence) {}
