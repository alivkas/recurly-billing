package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.services.AnalyticsService;
import ru.nocode.recurlybilling.services.TenantService;
import ru.nocode.recurlybilling.utils.docs.AnalyticsDocs;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "📊 Аналитика", description = AnalyticsDocs.TAG_DESCRIPTION)
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TenantService tenantService;

    @GetMapping
    @Operation(
            summary = AnalyticsDocs.GET_SUMMARY,
            description = AnalyticsDocs.GET_DESCRIPTION,
            tags = {"📊 Аналитика"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Аналитика получена",
                    content = @Content(
                            schema = @Schema(implementation = AnalyticsResponse.class),
                            examples = @ExampleObject(
                                    name = "Успешный ответ",
                                    value = AnalyticsDocs.GET_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "🔐 Неверные учётные данные тенанта",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 401,
                      "error": "Unauthorized",
                      "message": "Invalid API key or tenant ID"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Ошибка при вычислении аналитики",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 500,
                      "error": "Internal Server Error",
                      "message": "Failed to compute analytics: database timeout"
                    }
                    """
                    ))
            )
    })
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        log.info("Fetching analytics for tenant: {}", tenantId);

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);

            AnalyticsResponse response = analyticsService.getAnalytics(tenantId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {}", tenantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error fetching analytics for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}