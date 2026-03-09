package ru.nocode.recurlybilling.data.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record AnalyticsResponse(
        BigDecimal mrr,
        Long activeSubscriptions,
        Long totalCustomers,
        BigDecimal churnRate,
        Map<String, Long> subscriptionsByPlan,
        Map<LocalDate, BigDecimal> revenueByMonth,
        BigDecimal mrrGrowthRate,
        Long newSubscriptionsLast30Days   // Новые подписки за 30 дней
) {}