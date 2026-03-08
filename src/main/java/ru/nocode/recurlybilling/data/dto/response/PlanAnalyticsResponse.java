package ru.nocode.recurlybilling.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ru.nocode.recurlybilling.data.dto.PlanStats;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class PlanAnalyticsResponse {
    private String tenantId;
    private List<PlanStats> plans;
    private int totalPlans;
    private long totalRevenueCents;
    private LocalDateTime calculatedAt;
}
