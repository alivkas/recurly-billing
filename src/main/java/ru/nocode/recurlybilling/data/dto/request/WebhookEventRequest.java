package ru.nocode.recurlybilling.data.dto.request;

import java.util.Map;

public record WebhookEventRequest(
        String eventType,
        Map<String, Object> object
) {}
