package ru.nocode.recurlybilling.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.services.PlanService;
import ru.nocode.recurlybilling.services.TenantService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody PlanCreateRequest request) {

        log.info("Creating plan for tenant: {} with code: {}", tenantId, request.code());

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);

            PlanResponse response = planService.createPlan(tenantId, request);
            log.info("Successfully created plan: {} for tenant: {}", response.id(), tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {} - {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Unexpected error creating plan for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanResponse> getPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String planId) {

        tenantService.validateTenantAndApiKey(tenantId, apiKey);

        try {
            PlanResponse response = planService.getPlan(tenantId, planId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Plan not found: {} for tenant: {}", planId, tenantId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getAllPlans(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        tenantService.validateTenantAndApiKey(tenantId, apiKey);

        try {
            List<PlanResponse> plans = planService.getAllPlans(tenantId);
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error retrieving plans for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
