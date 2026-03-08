package ru.nocode.recurlybilling.data.dto.request;

import tools.jackson.databind.JsonNode;

public record YooKassaWebhookRequest(
        String id,
        String type,
        JsonNode event,
        JsonNode object
) {}