package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.StudentToken;
import ru.nocode.recurlybilling.data.entities.TemporaryCode;
import ru.nocode.recurlybilling.data.repositories.StudentTokenRepository;
import ru.nocode.recurlybilling.data.repositories.TemporaryCodeRepository;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAuthService {
    private final TemporaryCodeRepository temporaryCodeRepository;
    private final StudentTokenRepository studentTokenRepository;

    @Transactional
    public String generateTemporaryCode(String tenantId, String studentExternalId) {
        // Генерируем 6-значный код
        String code = String.format("%06d", (int) (Math.random() * 1000000));

        TemporaryCode tempCode = new TemporaryCode();
        tempCode.setTenantId(tenantId);
        tempCode.setStudentExternalId(studentExternalId);
        tempCode.setCode(code);
        tempCode.setIsUsed(false);
        tempCode.setExpiresAt(LocalDateTime.now().plusHours(24)); // Действует 24 часа

        temporaryCodeRepository.save(tempCode);
        log.info("Generated temporary code for student {}: {}", studentExternalId, code);
        return code;
    }

    @Transactional
    public String authenticateWithCode(String tenantId, String studentExternalId, String code) {
        TemporaryCode tempCode = temporaryCodeRepository
                .findByTenantIdAndStudentExternalIdAndCodeAndIsUsedFalse(tenantId, studentExternalId, code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired code"));

        if (tempCode.isExpired()) {
            throw new IllegalArgumentException("Code has expired");
        }

        // Помечаем код как использованный
        tempCode.setIsUsed(true);
        temporaryCodeRepository.save(tempCode);

        // Генерируем токен
        String token = UUID.randomUUID().toString();
        StudentToken studentToken = new StudentToken();
        studentToken.setTenantId(tenantId);
        studentToken.setStudentExternalId(studentExternalId);
        studentToken.setToken(token);
        studentToken.setExpiresAt(LocalDateTime.now().plusHours(1)); // Токен действует 1 час

        studentTokenRepository.save(studentToken);
        log.info("Student {} authenticated successfully", studentExternalId);
        return token;
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        return studentTokenRepository.findByToken(token)
                .map(t -> !t.isExpired())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String getStudentExternalIdByToken(String token) {
        return studentTokenRepository.findByToken(token)
                .filter(t -> !t.isExpired())
                .map(StudentToken::getStudentExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    }

    @Transactional(readOnly = true)
    public String getTenantIdByToken(String token) {
        return studentTokenRepository.findByToken(token)
                .filter(t -> !t.isExpired())
                .map(StudentToken::getTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
    }

    @Transactional
    public void cleanupExpiredTokens() {
        studentTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired student tokens");
    }
}
