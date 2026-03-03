package ru.nocode.recurlybilling.data.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WebhookEventRequest {
    private String eventType;
    private Map<String, Object> object;
}
