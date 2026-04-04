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
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.services.TenantService;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "🏢 Тенанты", description = """
    Управление организациями в системе рекуррентных платежей.
    
    **Авторизация**
    
    Защищённые эндпоинты требуют два заголовка:
    - `X-Tenant-ID` — идентификатор организации
    - `X-API-Key` — секретный ключ (выдаётся при регистрации)
    """)
public class TenantController {

    private final TenantService tenantService;

    /**
     * Создание тенанта
     * @param request запрос на создание
     * @return тело ответа тенанта
     */
    @PostMapping("/onboard")
    @Operation(
            summary = "📋 Зарегистрировать новую организацию",
            description = """
            Создаёт новую организацию в системе и выдаёт учётные данные для доступа к API.
            
            **Когда использовать:**
            - При подключении новой образовательной платформы к системе платежей
            - При регистрации нового клиента в вашей системе
            
            **Что происходит:**
            1. Валидация входных данных (название, контакты, настройки)
            2. Генерация уникального `tenantId` и `apiKey`
            3. Создание записи в базе данных
            4. Возврат учётных данных для дальнейшей авторизации
            
            **Важно:**
            - Метод не требует аутентификации (публичный эндпоинт)
            - После успешной регистрации сохраните `apiKey` — он не будет показан повторно
            - Данные обрабатываются в соответствии с ФЗ-152 «О персональных данных»
            """,
            tags = {"🏢 Тенанты"},
            security = {} // Публичный метод, без авторизации
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "✅ Организация успешно зарегистрирована",
                    content = @Content(schema = @Schema(implementation = TenantOnboardingResponse.class),
                            examples = @ExampleObject(
                                    name = "Успешная регистрация",
                                    value = """
                        {
                          "tenantId": "tn_7f8a9b2c3d4e5f6g",
                          "organizationName": "Онлайн-школа «ПрофИТ»",
                          "apiKey": "sk_live_9x8y7z6w5v4u3t2s1r0q",
                          "createdAt": "2024-03-31T10:30:00Z",
                          "status": "ACTIVE",
                          "paymentSettings": {
                            "provider": "YOOKASSA",
                            "currency": "RUB",
                            "recurringEnabled": true
                          }
                        }
                        """
                            ))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации входных данных",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Пустое название",
                                    value = """
                        {
                          "timestamp": "2024-03-31T10:30:00Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "organizationName: не может быть пустым"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Неверный формат телефона",
                                    value = """
                        {
                          "timestamp": "2024-03-31T10:30:00Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "contactPhone: неверный формат номера телефона"
                        }
                        """
                            )
                    })
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "⚠️ Организация с таким названием или email уже существует",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2024-03-31T10:30:00Z",
                      "status": 409,
                      "error": "Conflict",
                      "message": "Organization with email 'info@profit-school.ru' already exists"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Внутренняя ошибка сервера",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2024-03-31T10:30:00Z",
                      "status": 500,
                      "error": "Internal Server Error",
                      "message": "Произошла непредвиденная ошибка. Попробуйте позже."
                    }
                    """
                    ))
            )
    })
    public ResponseEntity<TenantOnboardingResponse> onboardTenant(
            @Valid @RequestBody TenantOnboardingRequest request) {

        log.info("Received onboarding request for organization: {}", request.organizationName());

        try {
            TenantOnboardingResponse response = tenantService.onboard(request);
            log.info("Successfully created tenant: {} with ID: {}",
                    request.organizationName(), response.getTenantId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid onboarding request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during tenant onboarding", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Получение информации о tenant'е
     * Требует аутентификации через X-Tenant-ID и X-API-Key
     */
    @GetMapping("/{tenantId}")
    @Operation(
            summary = "🔎 Получить данные организации",
            description = """
            Возвращает информацию о зарегистрированной организации.
            
            **Требования:**
            - `X-Tenant-ID` должен совпадать с `tenantId` в пути
            - `X-API-Key` должен быть валидным ключом организации
            """,
            tags = {"🏢 Тенанты"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные получены",
                    content = @Content(schema = @Schema(implementation = TenantOnboardingResponse.class),
                            examples = @ExampleObject(
                                    value = """
                        {
                          "tenantId": "6546tds",
                          "apiKey": "sk_test_XXXXXXXXXXXXXXXXXXXXXXXX",
                          "createdAt": "2026-03-31T22:27:38.9909694"
                        }
                        """
                            ))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Tenant ID не совпадает"),
            @ApiResponse(responseCode = "404", description = "❌ Организация не найдена"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
    public ResponseEntity<TenantOnboardingResponse> getTenant(
            @PathVariable String tenantId,
            @RequestHeader("X-Tenant-ID") String authTenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!tenantId.equals(authTenantId)) {
            log.warn("Tenant ID mismatch: path={}, header={}", tenantId, authTenantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TenantOnboardingResponse response = tenantService.getTenant(tenantId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Tenant not found: {}", tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Обновление настроек тенанта
     * @param tenantId id тенанта
     * @param apiKey API ключ тенанта
     * @param request запрос на обновление настроек
     * @return код статуса
     */
    @PatchMapping("/payment-settings")
    @Operation(
            summary = "⚙️ Настроить интеграцию с YooKassa",
            description = """
            Устанавливает учётные данные для подключения к платёжной системе ЮKassa.
            
            **Что нужно указать:**
            | Параметр | Описание | Где найти |
            |----------|----------|-----------|
            | `yookassaShopId` | ID магазина в ЮKassa | ЛК ЮKassa → Настройки → Техническая информация |
            | `yookassaSecretKey` | Секретный ключ | ЛК ЮKassa → Настройки → Ключи для интеграции |
            
            **Формат ключа:**
            - Тестовый режим: `test_` + 32+ символов (латиница, цифры, `-`, `_`)
            - Продакшен: `live_` + 32+ символов
            
            **Важно:**
            - Ключ хранится в зашифрованном виде
            - После установки можно проводить тестовые и реальные платежи
            - Для смены ключа отправьте новый запрос с обновлёнными данными
            """,
            tags = {"🏢 Тенанты"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "✅ Настройки сохранены"),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Неверный Shop ID",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "yookassaShopId: Shop ID must contain only digits"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Неверный формат ключа",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "yookassaSecretKey: Secret key must be a valid YooKassa key (starts with 'live_' or 'test_')"
                        }
                        """
                            )
                    })
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "❌ Организация не найдена"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка при сохранении")
    })
    public ResponseEntity<Void> updatePaymentSettings(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody TenantPaymentSettingsRequest request) {

        log.info("Updating payment settings for tenant: {}", tenantId);

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            tenantService.updatePaymentSettings(tenantId, request);
            log.info("Successfully updated payment settings for tenant: {}", tenantId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment settings request for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating payment settings for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
