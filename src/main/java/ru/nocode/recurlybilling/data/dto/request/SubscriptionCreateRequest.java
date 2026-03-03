package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SubscriptionCreateRequest {
    @NotBlank
    private String customerId;
    @NotBlank
    private String planId;
    @NotNull
    private LocalDate startDate;
}
