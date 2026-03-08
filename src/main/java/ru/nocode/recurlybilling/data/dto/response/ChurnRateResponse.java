package ru.nocode.recurlybilling.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class ChurnRateResponse {
    private String tenantId;
    private double churnRate;
    private double churnRatePercent;
    private long cancelledSubscriptions;
    private long activeSubscriptionsAtStart;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime calculatedAt;
}
