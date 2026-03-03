package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AnalyticsResponse {
    private long activeSubscriptions;
    private BigDecimal mrrRub;
    private double churnRate;
}
