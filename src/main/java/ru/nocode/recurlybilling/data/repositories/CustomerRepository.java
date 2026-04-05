package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Customer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantId(String tenantId);
    Long countByTenantId(String tenantId);
    Optional<Customer> findByTenantIdAndExternalId(String tenantId, String externalId);
    boolean existsByTenantIdAndExternalId(String tenantId, String externalId);
    Optional<Customer> findByTenantIdAndTelegramUsernameIgnoreCase(String tenantId, String telegramUsername);
    Optional<Customer> findByTelegramChatId(Long chatId);
    @Query("""
        SELECT COUNT(c)
        FROM Customer c
        WHERE c.tenantId = :tenantId 
          AND c.createdAt BETWEEN :start AND :end
        """)
    Long countNewCustomersByPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
