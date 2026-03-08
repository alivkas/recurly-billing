package ru.nocode.recurlybilling.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
public class WebhookEventResponse {
    private UUID id;
    private String tenantId;
    private String eventType;
    private String paymentId;
    private String status;
    private LocalDateTime createdAt;
}
