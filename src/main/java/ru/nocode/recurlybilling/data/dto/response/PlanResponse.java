package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class PlanResponse {
    private UUID id;
    private String code;
    private String name;
    private Long priceCents;
    private String currency;
    private String interval;
    private Integer intervalCount;
    private Integer trialDays;
    private LocalDate endDate;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}