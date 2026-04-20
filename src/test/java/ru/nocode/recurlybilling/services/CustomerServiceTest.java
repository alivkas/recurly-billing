package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nocode.recurlybilling.data.dto.request.CustomerCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.CustomerResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.services.tenant.EncryptionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private CustomerService customerService;

    private final String tenantId = "moscow_digital";
    private final String externalId = "student_123";
    private final String email = "student@example.com";
    private final String fullName = "Иван Иванов";
    private final String phone = "+79991234567";
    private final String encryptedValue = "encrypted_data_here";

    @Test
    @DisplayName("createCustomer() должен зашифровать чувствительные данные перед сохранением")
    void createCustomer_shouldEncryptSensitiveFields() {
        CustomerCreateRequest request = new CustomerCreateRequest(
                externalId, email, fullName, phone, true, null);

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(false);
        when(encryptionService.encrypt(email)).thenReturn(encryptedValue + "_email");
        when(encryptionService.encrypt(fullName)).thenReturn(encryptedValue + "_name");
        when(encryptionService.encrypt(phone)).thenReturn(encryptedValue + "_phone");

        Customer savedCustomer = new Customer();
        savedCustomer.setId(UUID.randomUUID());
        savedCustomer.setExternalId(externalId);
        savedCustomer.setEmail(encryptedValue + "_email");
        savedCustomer.setFullName(encryptedValue + "_name");
        savedCustomer.setPhone(encryptedValue + "_phone");
        savedCustomer.setIsStudent(true);
        savedCustomer.setCreatedAt(LocalDateTime.now());

        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);
        when(encryptionService.decrypt(encryptedValue + "_email")).thenReturn(email);
        when(encryptionService.decrypt(encryptedValue + "_name")).thenReturn(fullName);

        CustomerResponse response = customerService.createCustomer(tenantId, request);

        assertNotNull(response);
        assertEquals(externalId, response.externalId());
        assertEquals(email, response.email());
        assertEquals(fullName, response.fullName());
        assertTrue(response.isStudent());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer captured = captor.getValue();
        assertEquals(tenantId, captured.getTenantId());
        assertEquals(encryptedValue + "_email", captured.getEmail());
        assertEquals(encryptedValue + "_name", captured.getFullName());
        assertEquals(encryptedValue + "_phone", captured.getPhone());
        assertEquals(true, captured.getIsStudent());
    }

    @Test
    @DisplayName("createCustomer() должен выбросить исключение при дублирующемся externalId")
    void createCustomer_whenExternalIdExists_shouldThrow() {
        CustomerCreateRequest request = new CustomerCreateRequest(
                externalId, email, fullName, phone, true, null);

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                customerService.createCustomer(tenantId, request));

        verify(customerRepository, never()).save(any());
        verify(encryptionService, never()).encrypt(any());
    }

    @Test
    @DisplayName("createCustomer() должен обработать null telegramUsername")
    void createCustomer_withNullTelegramUsername_shouldNotSetUsername() {
        CustomerCreateRequest request = new CustomerCreateRequest(
                externalId, email, fullName, phone, false, null);

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(false);
        when(encryptionService.encrypt(anyString())).thenReturn(encryptedValue);

        Customer savedCustomer = new Customer();
        savedCustomer.setId(UUID.randomUUID());
        savedCustomer.setExternalId(externalId);
        savedCustomer.setTelegramUsername(null);
        savedCustomer.setCreatedAt(LocalDateTime.now());

        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        customerService.createCustomer(tenantId, request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertNull(captor.getValue().getTelegramUsername());
    }

    @Test
    @DisplayName("createCustomer() должен сохранить telegramUsername без @ и в нижнем регистре")
    void createCustomer_withTelegramUsername_shouldNormalize() {
        CustomerCreateRequest request = new CustomerCreateRequest(
                externalId, email, fullName, phone, true, "@StudentName");

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(false);
        when(encryptionService.encrypt(anyString())).thenReturn(encryptedValue);

        Customer savedCustomer = new Customer();
        savedCustomer.setId(UUID.randomUUID());
        savedCustomer.setExternalId(externalId);
        savedCustomer.setTelegramUsername("studentname");
        savedCustomer.setCreatedAt(LocalDateTime.now());

        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        customerService.createCustomer(tenantId, request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
    }

    @Test
    @DisplayName("getCustomerByExternalId() должен вернуть расшифрованные данные")
    void getCustomerByExternalId_shouldReturnDecryptedData() {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setExternalId(externalId);
        customer.setEmail(encryptedValue + "_email");
        customer.setFullName(encryptedValue + "_name");
        customer.setIsStudent(true);
        customer.setCreatedAt(LocalDateTime.now());

        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.of(customer));
        when(encryptionService.decrypt(encryptedValue + "_email")).thenReturn(email);
        when(encryptionService.decrypt(encryptedValue + "_name")).thenReturn(fullName);

        CustomerResponse response = customerService.getCustomerByExternalId(tenantId, externalId);

        assertNotNull(response);
        assertEquals(externalId, response.externalId());
        assertEquals(email, response.email());
        assertEquals(fullName, response.fullName());
        assertTrue(response.isStudent());

        verify(encryptionService).decrypt(encryptedValue + "_email");
        verify(encryptionService).decrypt(encryptedValue + "_name");
    }

    @Test
    @DisplayName("getCustomerByExternalId() должен выбросить исключение, если клиент не найден")
    void getCustomerByExternalId_whenNotFound_shouldThrow() {
        when(customerRepository.findByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                customerService.getCustomerByExternalId(tenantId, externalId));
    }

    @Test
    @DisplayName("getAllCustomers() должен вернуть список с расшифрованными данными")
    void getAllCustomers_shouldReturnDecryptedList() {
        Customer c1 = new Customer();
        c1.setId(UUID.randomUUID());
        c1.setExternalId("student_1");
        c1.setEmail(encryptedValue + "_1");
        c1.setFullName(encryptedValue + "_name1");
        c1.setIsStudent(true);
        c1.setCreatedAt(LocalDateTime.now());

        Customer c2 = new Customer();
        c2.setId(UUID.randomUUID());
        c2.setExternalId("student_2");
        c2.setEmail(encryptedValue + "_2");
        c2.setFullName(encryptedValue + "_name2");
        c2.setIsStudent(false);
        c2.setCreatedAt(LocalDateTime.now());

        when(customerRepository.findByTenantId(tenantId)).thenReturn(List.of(c1, c2));
        when(encryptionService.decrypt(encryptedValue + "_1")).thenReturn("a@test.com");
        when(encryptionService.decrypt(encryptedValue + "_name1")).thenReturn("Alice");
        when(encryptionService.decrypt(encryptedValue + "_2")).thenReturn("b@test.com");
        when(encryptionService.decrypt(encryptedValue + "_name2")).thenReturn("Bob");

        List<CustomerResponse> result = customerService.getAllCustomers(tenantId);

        assertEquals(2, result.size());

        CustomerResponse r1 = result.get(0);
        assertEquals("student_1", r1.externalId());
        assertEquals("a@test.com", r1.email());
        assertEquals("Alice", r1.fullName());
        assertTrue(r1.isStudent());

        CustomerResponse r2 = result.get(1);
        assertEquals("student_2", r2.externalId());
        assertEquals("b@test.com", r2.email());
        assertEquals("Bob", r2.fullName());
        assertFalse(r2.isStudent());

        verify(encryptionService, times(4)).decrypt(anyString());
    }

    @Test
    @DisplayName("getAllCustomers() должен вернуть пустой список, если клиентов нет")
    void getAllCustomers_whenEmpty_shouldReturnEmptyList() {
        when(customerRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<CustomerResponse> result = customerService.getAllCustomers(tenantId);

        assertTrue(result.isEmpty());
        verify(encryptionService, never()).decrypt(any());
    }

    @Test
    @DisplayName("validateTenantAndApiKey() должен делегировать проверку в TenantService")
    void validateTenantAndApiKey_shouldDelegateToTenantService() {
        String apiKey = "sk_live_valid_key";

        customerService.validateTenantAndApiKey(tenantId, apiKey);

        verify(tenantService).validateTenantAndApiKey(tenantId, apiKey);
    }

    @Test
    @DisplayName("validateTenantAndApiKey() должен пробрасывать исключение от TenantService")
    void validateTenantAndApiKey_shouldPropagateException() {
        String apiKey = "invalid_key";

        doThrow(new SecurityException("Invalid API key"))
                .when(tenantService).validateTenantAndApiKey(tenantId, apiKey);

        assertThrows(SecurityException.class, () ->
                customerService.validateTenantAndApiKey(tenantId, apiKey));
    }

    @Test
    @DisplayName("createCustomer() должен обработать пустой telegramUsername как null")
    void createCustomer_withEmptyTelegramUsername_shouldTreatAsNull() {
        CustomerCreateRequest request = new CustomerCreateRequest(
                externalId, email, fullName, phone, true, "   ");

        when(customerRepository.existsByTenantIdAndExternalId(tenantId, externalId))
                .thenReturn(false);
        when(encryptionService.encrypt(anyString())).thenReturn(encryptedValue);

        Customer savedCustomer = new Customer();
        savedCustomer.setId(UUID.randomUUID());
        savedCustomer.setExternalId(externalId);
        savedCustomer.setCreatedAt(LocalDateTime.now());

        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        customerService.createCustomer(tenantId, request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
    }
}