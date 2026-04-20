package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.StudentAccess;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.StudentAccessRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

    @Mock
    private StudentAccessRepository accessRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AccessService accessService;

    private final UUID customerId = UUID.randomUUID();
    private final String tenantId = "moscow_digital";
    private final String planCode = "math_premium";
    private final String externalId = "student_123";

    @Test
    @DisplayName("grantAccess() должен создать новую запись доступа, если активной нет")
    void grantAccess_whenNoActiveAccess_shouldCreateNew() {
        LocalDate periodEnd = LocalDate.now().plusMonths(1);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of());

        accessService.grantAccess(tenantId, customerId, planCode, periodEnd);

        ArgumentCaptor<StudentAccess> captor = ArgumentCaptor.forClass(StudentAccess.class);
        verify(accessRepository).save(captor.capture());

        StudentAccess saved = captor.getValue();
        assertEquals(customerId, saved.getStudentId());
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(planCode, saved.getPlanCode());
        assertEquals(periodEnd, saved.getAccessExpiresAt());
        assertEquals(StudentAccess.AccessStatus.ACTIVE, saved.getStatus());
    }

    @Test
    @DisplayName("grantAccess() должен продлить доступ, если новый период позже текущего")
    void grantAccess_whenNewPeriodLater_shouldExtend() {
        LocalDate currentEnd = LocalDate.now().plusDays(10);
        LocalDate newEnd = LocalDate.now().plusDays(20);

        StudentAccess existing = new StudentAccess();
        existing.setStudentId(customerId);
        existing.setPlanCode(planCode);
        existing.setAccessExpiresAt(currentEnd);
        existing.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of(existing));

        accessService.grantAccess(tenantId, customerId, planCode, newEnd);

        assertEquals(newEnd, existing.getAccessExpiresAt());
        verify(accessRepository).save(existing);
    }

    @Test
    @DisplayName("grantAccess() не должен менять доступ, если новый период раньше текущего")
    void grantAccess_whenNewPeriodEarlier_shouldNotChange() {
        LocalDate currentEnd = LocalDate.now().plusDays(20);
        LocalDate newEnd = LocalDate.now().plusDays(10);

        StudentAccess existing = new StudentAccess();
        existing.setStudentId(customerId);
        existing.setPlanCode(planCode);
        existing.setAccessExpiresAt(currentEnd);
        existing.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of(existing));

        accessService.grantAccess(tenantId, customerId, planCode, newEnd);

        assertEquals(currentEnd, existing.getAccessExpiresAt());
        verify(accessRepository, never()).save(any());
    }

    @Test
    @DisplayName("revokeAccess() должен изменить статус на REVOKED")
    void revokeAccess_shouldSetStatusRevoked() {
        UUID accessId = UUID.randomUUID();
        StudentAccess access = new StudentAccess();
        access.setId(accessId);
        access.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findById(accessId)).thenReturn(Optional.of(access));

        accessService.revokeAccess(accessId);

        assertEquals(StudentAccess.AccessStatus.REVOKED, access.getStatus());
        verify(accessRepository).save(access);
    }

    @Test
    @DisplayName("revokeAccess() должен выбросить исключение, если доступ не найден")
    void revokeAccess_whenNotFound_shouldThrow() {
        UUID accessId = UUID.randomUUID();
        when(accessRepository.findById(accessId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                accessService.revokeAccess(accessId));
    }

    @Test
    @DisplayName("revokeAccessImmediately() должен выбросить исключение, если клиент не найден")
    void revokeAccessImmediately_whenCustomerNotFound_shouldThrow() {
        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                accessService.revokeAccessImmediately(externalId, tenantId, planCode));
    }

    @Test
    @DisplayName("hasActiveAccess() должен вернуть true, если доступ активен и не истёк")
    void hasActiveAccess_whenValid_shouldReturnTrue() {
        StudentAccess access = new StudentAccess();
        access.setAccessExpiresAt(LocalDate.now().plusDays(10));
        access.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of(access));

        boolean result = accessService.hasActiveAccess(customerId, planCode);

        assertTrue(result);
    }

    @Test
    @DisplayName("hasActiveAccess() должен вернуть false, если доступ истёк")
    void hasActiveAccess_whenExpired_shouldReturnFalse() {
        StudentAccess access = new StudentAccess();
        access.setAccessExpiresAt(LocalDate.now().minusDays(1));
        access.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of(access));

        boolean result = accessService.hasActiveAccess(customerId, planCode);

        assertFalse(result);
    }

    @Test
    @DisplayName("hasActiveAccess() должен вернуть false, если активных записей нет")
    void hasActiveAccess_whenNoRecords_shouldReturnFalse() {
        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of());

        boolean result = accessService.hasActiveAccess(customerId, planCode);

        assertFalse(result);
    }

    @Test
    @DisplayName("getAccessExpiry() должен вернуть дату окончания доступа")
    void getAccessExpiry_whenExists_shouldReturnDate() {
        LocalDate expiry = LocalDate.now().plusMonths(1);
        StudentAccess access = new StudentAccess();
        access.setAccessExpiresAt(expiry);
        access.setStatus(StudentAccess.AccessStatus.ACTIVE);

        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of(access));

        LocalDate result = accessService.getAccessExpiry(customerId, planCode);

        assertEquals(expiry, result);
    }

    @Test
    @DisplayName("getAccessExpiry() должен вернуть null, если записей нет")
    void getAccessExpiry_whenNotExists_shouldReturnNull() {
        when(accessRepository.findByStudentIdAndPlanCodeAndStatus(
                customerId, planCode, StudentAccess.AccessStatus.ACTIVE))
                .thenReturn(List.of());

        LocalDate result = accessService.getAccessExpiry(customerId, planCode);

        assertNull(result);
    }

    @Test
    @DisplayName("isStudentBelongsToTenant() должен вернуть true, если подписка найдена")
    void isStudentBelongsToTenant_whenSubscriptionExists_shouldReturnTrue() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.of(customer));
        when(subscriptionRepository.existsByTenantIdAndCustomerId(tenantId, customerId))
                .thenReturn(true);

        boolean result = accessService.isStudentBelongsToTenant(externalId, tenantId);

        assertTrue(result);
        verify(subscriptionRepository).existsByTenantIdAndCustomerId(tenantId, customerId);
    }

    @Test
    @DisplayName("isStudentBelongsToTenant() должен вернуть false, если подписки нет")
    void isStudentBelongsToTenant_whenNoSubscription_shouldReturnFalse() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.of(customer));
        when(subscriptionRepository.existsByTenantIdAndCustomerId(tenantId, customerId))
                .thenReturn(false);

        boolean result = accessService.isStudentBelongsToTenant(externalId, tenantId);

        assertFalse(result);
    }

    @Test
    @DisplayName("isStudentBelongsToTenantByExternalId() должен делегировать проверку в репозиторий")
    void isStudentBelongsToTenantByExternalId_shouldDelegateToRepository() {
        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(true);

        boolean result = accessService.isStudentBelongsToTenantByExternalId(externalId, tenantId);

        assertTrue(result);
        verify(customerRepository).existsByTenantIdAndExternalId(tenantId, externalId);
    }
}