package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class PlanCreateRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Long priceCents;

    @NotBlank
    private String currency = "RUB";

    @NotBlank
    private String interval;

    @Min(1)
    private Integer intervalCount = 1;

    private Integer trialDays = 0;

    private LocalDate endDate;

    private Map<String, Object> metadata;

}
