package ru.nocode.recurlybilling.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.services.StudentAuthService;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentAuthController {
    private final StudentAuthService studentAuthService;

    @PostMapping("/login")
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
