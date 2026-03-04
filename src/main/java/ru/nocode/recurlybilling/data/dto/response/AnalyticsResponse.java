package ru.nocode.recurlybilling.data.dto.response;

import java.math.BigDecimal;

public record AnalyticsResponse(
        long activeSubscriptions,
        BigDecimal mrrRub,
        double churnRate
) {}