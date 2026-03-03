package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SubscriptionResponse {
    private UUID id;
    private String status;
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;
    private LocalDate nextBillingDate;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
