package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.StudentAccess;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.StudentAccessRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    private final StudentAccessRepository accessRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional
    public void grantAccess(String tenantId, String customerId, String planCode, LocalDate periodEnd) {
        List<StudentAccess> active = accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE
        );

        if (!active.isEmpty()) {
            StudentAccess existing = active.get(0);
            if (periodEnd.isAfter(existing.getAccessExpiresAt())) {
                existing.setAccessExpiresAt(periodEnd);
                accessRepository.save(existing);
                log.info("Extended access for student {} until {}", customerId, periodEnd);
            } else {
                log.info("Access already valid until {}, no extension needed", existing.getAccessExpiresAt());
            }
            return;
        }

        StudentAccess access = new StudentAccess();
        access.setStudentId(customerId);
        access.setTenantId(tenantId);
        access.setPlanCode(planCode);
        access.setAccessExpiresAt(periodEnd);
        access.setStatus(StudentAccess.AccessStatus.ACTIVE);

        accessRepository.save(access);
    }

    @Transactional
    public void revokeAccess(UUID accessId) {
        StudentAccess access = accessRepository.findById(accessId)
                .orElseThrow(() -> new IllegalArgumentException("Access not found"));
        access.setStatus(StudentAccess.AccessStatus.REVOKED);
        accessRepository.save(access);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deactivateExpiredAccess() {
        LocalDate today = LocalDate.now();
        List<StudentAccess> expired = accessRepository.findByAccessExpiresAtBeforeAndStatus(
                today, StudentAccess.AccessStatus.ACTIVE
        );

        int count = 0;
        for (StudentAccess access : expired) {
            access.setStatus(StudentAccess.AccessStatus.EXPIRED);
            count++;
        }

        accessRepository.saveAll(expired);
        log.info("Deactivated {} expired accesses", count);
    }

    public boolean hasActiveAccess(String studentId, String planCode) {
        List<StudentAccess> accesses = accessRepository.findByStudentIdAndPlanCodeAndStatus(
                studentId, planCode, StudentAccess.AccessStatus.ACTIVE
        );
        LocalDate today = LocalDate.now();
        return accesses.stream()
                .anyMatch(access -> !access.getAccessExpiresAt().isBefore(today));
    }

    public LocalDate getAccessExpiry(String studentId, String planCode) {
        return accessRepository.findByStudentIdAndPlanCodeAndStatus(studentId, planCode, StudentAccess.AccessStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(StudentAccess::getAccessExpiresAt)
                .orElse(null);
    }

    public boolean isStudentBelongsToTenant(String studentId, String tenantId) {
        boolean belongsToTenant = subscriptionRepository
                .existsByTenantIdAndCustomerId(tenantId, studentId);
        return belongsToTenant;
    }

    public void revokeAccessOnPaymentFailure(String customerId, String planCode) {
        List<StudentAccess> accesses = accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE
        );
        for (StudentAccess access : accesses) {
            access.setStatus(StudentAccess.AccessStatus.EXPIRED);
        }
        accessRepository.saveAll(accesses);
        log.info("Revoked access for student {} due to payment failure", customerId);
    }
}