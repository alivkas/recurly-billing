package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createCustomerShouldSaveAndReturnResponse() {
        String tenantId = "moscow_digital_school";
        var request = new CustomerCreateRequest(
                "user_12345",
                "student@example.com",
                "Иванов Иван Иванович",
                "+79991234567",
                true
        );

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, "user_12345")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(encryptionService.encrypt("student@example.com")).thenReturn("encrypted_email".getBytes());
        when(encryptionService.encrypt("Иванов Иван Иванович")).thenReturn("encrypted_name".getBytes());
        when(encryptionService.encrypt("+79991234567")).thenReturn("encrypted_phone".getBytes());
        when(encryptionService.decrypt(any(byte[].class))).thenReturn("student@example.com");

        CustomerResponse response = customerService.createCustomer(tenantId, request);

        assertThat(response.externalId()).isEqualTo("user_12345");
        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.isStudent()).isTrue();

        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void createCustomerWithDuplicateExternalIdShouldThrowException() {
        String tenantId = "moscow_digital_school";
        var request = new CustomerCreateRequest("user_12345", null, null, null, true);

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, "user_12345")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getCustomerShouldReturnDecryptedData() {
        String tenantId = "moscow_digital_school";
        String externalId = "user_12345";

        Customer customer = new Customer();
        customer.setId(java.util.UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setExternalId(externalId);
        customer.setEmail("encrypted_email".getBytes());
        customer.setFullName("encrypted_name".getBytes());
        customer.setIsStudent(true);

        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.of(customer));
        when(encryptionService.decrypt("encrypted_email".getBytes())).thenReturn("student@example.com");
        when(encryptionService.decrypt("encrypted_name".getBytes())).thenReturn("Иванов И.И.");

        CustomerResponse response = customerService.getCustomer(tenantId, externalId);

        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.fullName()).isEqualTo("Иванов И.И.");
    }
}