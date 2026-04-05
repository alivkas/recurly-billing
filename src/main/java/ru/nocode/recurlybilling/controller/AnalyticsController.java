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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.components.CSV.AnalyticsCsvExporter;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.services.AnalyticsService;
import ru.nocode.recurlybilling.services.TenantService;
import ru.nocode.recurlybilling.utils.docs.AnalyticsDocs;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "📊 Аналитика", description = AnalyticsDocs.TAG_DESCRIPTION)
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TenantService tenantService;
    private final AnalyticsCsvExporter csvExporter;

    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    @GetMapping("/export")
    @Operation(
            summary = "📥 Экспорт аналитики в CSV",
            description = """
            Скачивает отчёт в формате CSV, совместимый с Excel и Google Sheets.
            
            **Структура файла:**
            1. Строка заголовков
            2. Одна строка с основными метриками за период
            3. (Опционально) Таблица ежедневной выручки для построения графиков
            """,
            tags = {"📊 Аналитика"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "✅ CSV-файл сгенерирован"),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка генерации отчёта")
    })
    public ResponseEntity<Resource> exportAnalyticsCsv(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            AnalyticsResponse analytics = analyticsService.getAnalytics(tenantId);
            String csvContent = csvExporter.exportToCsv(analytics);
            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
            String filename = String.format("analytics_%s.csv", LocalDate.now().format(FILE_DATE_FMT));

            Resource resource = new ByteArrayResource(csvBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .contentLength(csvBytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to export analytics to CSV for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}