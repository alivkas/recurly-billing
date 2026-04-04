package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.services.StudentAuthService;
import ru.nocode.recurlybilling.utils.docs.StudentAuthDocs;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@Tag(name = "🔑 Аутентификация", description = StudentAuthDocs.TAG_DESCRIPTION)
public class StudentAuthController {
    private final StudentAuthService studentAuthService;

    @PostMapping("/login")
    @Operation(
            summary = StudentAuthDocs.LOGIN_SUMMARY,
            description = StudentAuthDocs.LOGIN_DESCRIPTION,
            tags = {"🔑 Аутентификация"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Аутентификация успешна",
                    content = @Content(
                            schema = @Schema(),
                            examples = @ExampleObject(
                                    name = "Успешный вход",
                                    value = StudentAuthDocs.LOGIN_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации или неверный код",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Неверный код",
                                            value = """
                            { "error": "INVALID_CODE: Код не совпадает с выданным" }
                            """
                                    ),
                                    @ExampleObject(
                                            name = "Код истёк",
                                            value = """
                            { "error": "CODE_EXPIRED: Срок действия кода истёк. Запросите новый." }
                            """
                                    ),
                                    @ExampleObject(
                                            name = "Слишком много попыток",
                                            value = """
                            { "error": "TOO_MANY_ATTEMPTS: Превышен лимит попыток. Повторите через 15 минут." }
                            """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "❌ Студент или тенант не найден",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "STUDENT_NOT_FOUND: Student with ID 'student_unknown' not found in tenant" }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Внутренняя ошибка сервера",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "Internal server error" }
                    """
                    ))
            )
    })
    public ResponseEntity<Map<String, String>> login(
            @RequestBody LoginRequest request) {
        try {
            String token = studentAuthService.authenticateWithCode(
                    request.getTenantId(),
                    request.getStudentExternalId(),
                    request.getCode()
            );
            return ResponseEntity.ok(Map.of("token", token));
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }

    @Data
    public static class LoginRequest {
        private String tenantId;
        private String studentExternalId;
        private String code;
    }
}
