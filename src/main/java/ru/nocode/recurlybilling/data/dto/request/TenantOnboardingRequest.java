package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantOnboardingRequest {
    @NotNull
    private String companyName;
    @NotNull
    private String contactEmail;
}
