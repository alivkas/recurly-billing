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
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.services.CustomerService;
import ru.nocode.recurlybilling.utils.docs.CustomerDocs;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "👥 Клиенты", description = CustomerDocs.TAG_DESCRIPTION)
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @Operation(
            summary = CustomerDocs.CREATE_SUMMARY,
            description = CustomerDocs.CREATE_DESCRIPTION,
            tags = {"👥 Клиенты"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "✅ Клиент успешно создан",
                    content = @Content(
                            schema = @Schema(implementation = CustomerResponse.class),
                            examples = @ExampleObject(
                                    name = "Успешное создание",
                                    value = CustomerDocs.CREATE_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации данных",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Дубликат externalId",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Customer with externalId 'user_ivan_2024' already exists for this tenant"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Неверный формат email",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "email: неверный формат адреса электронной почты"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Неверный формат телефона",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "phone: номер должен быть в формате +79991234567"
                        }
                        """
                            )
                    })
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные тенанта"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "💥 Внутренняя ошибка сервера")
    })
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody CustomerCreateRequest request) {

        log.info("Creating customer for tenant: {} with externalId: {}", tenantId, request.externalId());

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            CustomerResponse response = customerService.createCustomer(tenantId, request);
            log.info("Successfully created customer: {} for tenant: {}", response.id(), tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid customer creation request for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating customer for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{externalId}")
    @Operation(
            summary = CustomerDocs.GET_SUMMARY,
            description = CustomerDocs.GET_DESCRIPTION,
            tags = {"👥 Клиенты"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные клиента получены",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "404", description = "❌ Клиент не найден"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
    public ResponseEntity<CustomerResponse> getCustomer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String externalId) {

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            CustomerResponse response = customerService.getCustomerByExternalId(tenantId, externalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Customer not found: {} for tenant: {}", externalId, tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving customer: {} for tenant: {}", externalId, tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @Operation(
            summary = CustomerDocs.LIST_SUMMARY,
            description = CustomerDocs.LIST_DESCRIPTION,
            tags = {"👥 Клиенты"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Список клиентов получен",
                    content = @Content(schema = @Schema(implementation = CustomerResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
    public ResponseEntity<List<CustomerResponse>> getAllCustomers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            List<CustomerResponse> customers = customerService.getAllCustomers(tenantId);
            return ResponseEntity.ok(customers);
        } catch (Exception e) {
            log.error("Error retrieving customers for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}