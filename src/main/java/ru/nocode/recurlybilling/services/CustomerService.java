package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.services.tenant.EncryptionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final EncryptionService encryptionService;
    private final TenantService tenantService; // для валидации

    @Transactional
    public CustomerResponse createCustomer(String tenantId, CustomerCreateRequest request) {
        if (customerRepository.existsByTenantIdAndExternalId(tenantId, request.externalId())) {
            throw new IllegalArgumentException("Customer with externalId '" + request.externalId() + "' already exists");
        }

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setExternalId(request.externalId());
        customer.setEmail(encryptionService.encrypt(request.email()));
        customer.setFullName(encryptionService.encrypt(request.fullName()));
        customer.setPhone(encryptionService.encrypt(request.phone()));
        customer.setIsStudent(request.isStudent());

        if (request.telegramUsername() != null && !request.telegramUsername().isBlank()) {
            customer.setTelegramUsername(request.telegramUsername()); // ← сеттер автоматически уберёт @ и приведёт к нижнему регистру
        }

        customer.setCreatedAt(LocalDateTime.now());
        Customer saved = customerRepository.save(customer);
        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByExternalId(String tenantId, String externalId) {
        Customer customer = customerRepository.findByTenantIdAndExternalId(tenantId, externalId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + externalId));
        return convertToResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers(String tenantId) {
        List<Customer> customers = customerRepository.findByTenantId(tenantId);
        return customers.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public void validateTenantAndApiKey(String tenantId, String apiKey) {
        tenantService.validateTenantAndApiKey(tenantId, apiKey);
    }

    private CustomerResponse convertToResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getExternalId(),
                encryptionService.decrypt(customer.getEmail()),
                encryptionService.decrypt(customer.getFullName()),
                customer.getIsStudent(),
                customer.getCreatedAt()
        );
    }
}
