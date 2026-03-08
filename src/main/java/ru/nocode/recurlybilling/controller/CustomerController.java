package ru.nocode.recurlybilling.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.services.CustomerService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody CustomerCreateRequest request) {

        log.info("Creating customer for tenant: {} with externalId: {}", tenantId, request.externalId());

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            CustomerResponse response = customerService.createCustomer(tenantId, request);
            log.info("Successfully created customer: {} for tenant: {}", response.id(), tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid customer creation request for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating customer for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{externalId}")
    public ResponseEntity<CustomerResponse> getCustomer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String externalId) {

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            CustomerResponse response = customerService.getCustomerByExternalId(tenantId, externalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Customer not found: {} for tenant: {}", externalId, tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving customer: {} for tenant: {}", externalId, tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        try {
            customerService.validateTenantAndApiKey(tenantId, apiKey);

            List<CustomerResponse> customers = customerService.getAllCustomers(tenantId);
            return ResponseEntity.ok(customers);
        } catch (Exception e) {
            log.error("Error retrieving customers for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}