package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlanResponse createPlan(String tenantId, PlanCreateRequest request) {
        validateRequest(request);

        if (planRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new IllegalArgumentException("Plan with code '" + request.code() + "' already exists for tenant '" + tenantId + "'");
        }

        Plan plan = new Plan();
        plan.setId(UUID.randomUUID());
        plan.setTenantId(tenantId);
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setPriceCents(request.priceCents());
        plan.setCurrency(request.currency());
        plan.setInterval(request.interval());
        plan.setIntervalCount(request.intervalCount());
        plan.setTrialDays(request.trialDays());
        plan.setStartDate(request.startDate());
        plan.setEndDate(determineEndDate(request));
        plan.setIsActive(true);
        plan.setCreatedAt(LocalDateTime.now());

        if (request.metadata() != null && !request.metadata().isEmpty()) {
            plan.setMetadata(objectMapper.valueToTree(request.metadata()));
        } else {
            plan.setMetadata(objectMapper.createObjectNode());
        }

        Plan saved = planRepository.save(plan);

        return new PlanResponse(
                saved.getId(),
                saved.getCode(),
                saved.getName(),
                saved.getPriceCents(),
                saved.getCurrency(),
                saved.getInterval(),
                saved.getIntervalCount(),
                saved.getTrialDays(),
                saved.getStartDate(),
                saved.getEndDate(),
                objectMapper.convertValue(saved.getMetadata(), Map.class),
                saved.getCreatedAt()
        );
    }

    private void validateRequest(PlanCreateRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Plan code is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Plan name is required");
        }
        if (request.priceCents() == null || request.priceCents() < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
        if (request.intervalCount() <= 0) {
            throw new IllegalArgumentException("intervalCount must be >= 1");
        }
        if (request.trialDays() < 0) {
            throw new IllegalArgumentException("trialDays must be >= 0");
        }
        validateInterval(request.interval());
    }

    private void validateInterval(String interval) {
        Set<String> allowed = Set.of("month", "semester", "year", "custom");
        if (!allowed.contains(interval)) {
            throw new IllegalArgumentException("Unsupported interval: " + interval + ". Allowed: " + allowed);
        }
    }

    private LocalDate determineEndDate(PlanCreateRequest request) {
        if (request.endDate() != null) {
            return request.endDate();
        }

        if (request.startDate() == null) {
            throw new IllegalArgumentException("Either endDate or startDate must be provided");
        }

        return switch (request.interval()) {
            case "semester" -> calculateSemesterEndDate(request.startDate());
            case "month" -> request.startDate().plusMonths(request.intervalCount());
            case "year" -> request.startDate().plusYears(request.intervalCount());
            case "custom" -> throw new IllegalArgumentException("For 'custom' interval, endDate must be specified");
            default -> throw new IllegalStateException("Unexpected interval: " + request.interval());
        };
    }

    private LocalDate calculateSemesterEndDate(LocalDate startDate) {
        int month = startDate.getMonthValue();
        if (month >= 9) {
            return LocalDate.of(startDate.getYear(), 12, 31);
        } else if (month >= 2) {
            return LocalDate.of(startDate.getYear(), 5, 31);
        } else {
            return LocalDate.of(startDate.getYear() + 1, 12, 31);
        }
    }
}
