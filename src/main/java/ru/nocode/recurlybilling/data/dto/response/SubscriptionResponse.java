package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class SubscriptionResponse {
    private String id;
    private String status;
    private LocalDate currentPeriodStart;
    private LocalDate currentPeriodEnd;
    private LocalDate nextBillingDate;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
