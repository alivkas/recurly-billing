package ru.nocode.recurlybilling.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class AnalyticsResponse {
    private String tenantId;
    private long mrrCents;
    private double mrrRub;
    private long semesterRevenueCents;
    private double semesterRevenueRub;
    private int activeRecurringSubscriptions;
    private int activeSemesterSubscriptions;
    private int totalActiveSubscriptions;
    private LocalDateTime calculatedAt;
}