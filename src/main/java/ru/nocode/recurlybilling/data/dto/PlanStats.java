package ru.nocode.recurlybilling.data.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PlanStats {
    private String planId;
    private String planCode;
    private String planName;
    private String interval;
    private long priceCents;
    private int totalSubscriptions;
    private int activeSubscriptions;
    private int cancelledSubscriptions;
    private long revenueCents;
}
