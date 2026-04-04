package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.nocode.recurlybilling.utils.docs.PlanDocs;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "💰 Тарифные планы", description = PlanDocs.TAG_DESCRIPTION)
public class PlanController {

    private final PlanService planService;
    private final TenantService tenantService;

    @PostMapping
    @Operation(
            summary = PlanDocs.CREATE_SUMMARY,
            description = PlanDocs.CREATE_DESCRIPTION,
            tags = {"💰 Тарифные планы"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "✅ План успешно создан",
                    content = @Content(
                            schema = @Schema(implementation = PlanResponse.class),
                            examples = @ExampleObject(
                                    name = "Успешное создание",
                                    value = PlanDocs.CREATE_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации параметров",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Дубликат кода плана",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Plan with code 'premium_monthly' already exists for this tenant"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Неверная валюта",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "currency: unsupported value 'EUR'. Supported: RUB, USD, KZT"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Отрицательная сумма",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "amount: must be greater than 0"
                        }
                        """
                            )
                    })
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные тенанта"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав для создания планов"),
            @ApiResponse(responseCode = "500", description = "💥 Внутренняя ошибка сервера")
    })
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
    @Operation(
            summary = PlanDocs.GET_SUMMARY,
            description = PlanDocs.GET_DESCRIPTION,
            tags = {"💰 Тарифные планы"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные плана получены",
                    content = @Content(schema = @Schema(implementation = PlanResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "404", description = "❌ План не найден"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
    @Operation(
            summary = PlanDocs.LIST_SUMMARY,
            description = PlanDocs.LIST_DESCRIPTION,
            tags = {"💰 Тарифные планы"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Список планов получен",
                    content = @Content(schema = @Schema(implementation = PlanResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
