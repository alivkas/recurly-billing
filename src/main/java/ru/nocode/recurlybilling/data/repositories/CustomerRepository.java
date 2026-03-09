package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantId(String tenantId);
    Long countByTenantId(String tenantId);
    Optional<Customer> findByTenantIdAndExternalId(String tenantId, String externalId);
    boolean existsByTenantIdAndExternalId(String tenantId, String externalId);
}
