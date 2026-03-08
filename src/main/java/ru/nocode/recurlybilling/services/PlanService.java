package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlanResponse createPlan(String tenantId, PlanCreateRequest request) {
        validateInterval(request.interval());

        Plan planToSave = new Plan();
        planToSave.setTenantId(tenantId);
        planToSave.setCode(request.code());
        planToSave.setName(request.name());
        planToSave.setPriceCents(request.priceCents());
        planToSave.setCurrency(request.currency());
        planToSave.setInterval(request.interval());
        planToSave.setIntervalCount(request.intervalCount());
        planToSave.setTrialDays(request.trialDays());
        planToSave.setStartDate(request.startDate());
        planToSave.setEndDate(request.endDate());

        if (request.metadata() != null && !request.metadata().isEmpty()) {
            planToSave.setMetadata(objectMapper.valueToTree(request.metadata()));
        } else {
            planToSave.setMetadata(objectMapper.createObjectNode());
        }

        planToSave.setIsActive(true);
        planToSave.setCreatedAt(LocalDateTime.now());

        log.info("Creating plan with ID: {}", planToSave.getId());

        try {
            Plan savedPlan = planRepository.save(planToSave);
            log.info("Plan saved with ID: {}", savedPlan.getId());
            return convertToResponse(savedPlan);
        } catch (DataIntegrityViolationException e) {
            log.warn("Plan with code '{}' already exists for tenant '{}'", request.code(), tenantId);
            throw new IllegalArgumentException("Plan with code '" + request.code() + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(String tenantId, String planId) {
        Plan plan = planRepository.findByIdAndTenantId(UUID.fromString(planId), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        return convertToResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getAllPlans(String tenantId) {
        List<Plan> plans = planRepository.findByTenantId(tenantId);
        return plans.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private void validateInterval(String interval) {
        Set<String> allowed = Set.of("month", "semester", "year", "custom");
        if (!allowed.contains(interval)) {
            throw new IllegalArgumentException("Unsupported interval: " + interval + ". Allowed: " + allowed);
        }
    }

    private PlanResponse convertToResponse(Plan plan) {
        Map<String, Object> metadata = null;
        if (plan.getMetadata() != null) {
            metadata = objectMapper.convertValue(plan.getMetadata(), Map.class);
        }

        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getPriceCents(),
                plan.getCurrency(),
                plan.getInterval(),
                plan.getIntervalCount(),
                plan.getTrialDays(),
                plan.getStartDate(),
                plan.getEndDate(),
                metadata,
                plan.getCreatedAt()
        );
    }
}
