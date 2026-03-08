package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    @Transactional
    public CustomerResponse createCustomer(String tenantId, CustomerCreateRequest request) {
        if (customerRepository.existsByTenantIdAndExternalId(tenantId, request.externalId())) {
            throw new IllegalArgumentException("Customer with externalId '" + request.externalId() + "' already exists for tenant '" + tenantId + "'");
        }

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setExternalId(request.externalId());
        customer.setIsStudent(request.isStudent() != null ? request.isStudent() : true);

        if (request.email() != null && !request.email().isBlank()) {
            customer.setEmail(encryptionService.encrypt(request.email()));
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            customer.setFullName(encryptionService.encrypt(request.fullName()));
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            customer.setPhone(encryptionService.encrypt(request.phone()));
        }

        customer.setIsActive(true);
        customer.setCreatedAt(LocalDateTime.now());

        Customer saved = customerRepository.save(customer);

        auditLogService.logEvent(
                tenantId,
                "system",
                "CREATE_CUSTOMER",
                "CUSTOMER",
                saved.getId().toString(),
                null,
                Map.of("externalId", request.externalId()),
                null,
                null
        );

        return new CustomerResponse(
                saved.getId().toString(),
                saved.getExternalId(),
                saved.getEmail() != null && saved.getEmail().length > 0 ? encryptionService.decrypt(saved.getEmail()) : null,
                saved.getFullName() != null && saved.getFullName().length > 0 ? encryptionService.decrypt(saved.getFullName()) : null,
                saved.getIsStudent(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(String tenantId, String externalId) {
        Customer customer = customerRepository.findByTenantIdAndExternalId(tenantId, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Customer with externalId '" + externalId + "' not found for tenant '" + tenantId + "'"));

        return new CustomerResponse(
                customer.getId().toString(),
                customer.getExternalId(),
                customer.getEmail() != null && customer.getEmail().length > 0 ? encryptionService.decrypt(customer.getEmail()) : null,
                customer.getFullName() != null && customer.getFullName().length > 0 ? encryptionService.decrypt(customer.getFullName()) : null,
                customer.getIsStudent(),
                customer.getCreatedAt()
        );
    }

    @Transactional
    public void deactivateCustomer(String tenantId, String externalId) {
        Customer customer = customerRepository.findByTenantIdAndExternalId(tenantId, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Customer with externalId '" + externalId + "' not found for tenant '" + tenantId + "'"));

        customer.setIsActive(false);
        customerRepository.save(customer);
    }
}
