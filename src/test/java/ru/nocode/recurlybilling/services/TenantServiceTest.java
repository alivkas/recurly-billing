package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.services.tenant.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantAuthenticationService tenantAuthenticationService;

    @Mock
    private TenantLifeCycleService tenantLifeCycleService;

    @Mock
    private TenantPaymentSettingsService tenantPaymentSettingsService;

    @Mock
    private TenantQueryService tenantQueryService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    @DisplayName("onboard() должен делегировать вызов в TenantLifeCycleService и вернуть ответ")
    void onboard_shouldDelegateAndReturnResponse() {
        TenantOnboardingRequest request = mock(TenantOnboardingRequest.class);
        TenantOnboardingResponse expected = mock(TenantOnboardingResponse.class);

        when(tenantLifeCycleService.onboard(request)).thenReturn(expected);

        TenantOnboardingResponse actual = tenantService.onboard(request);

        assertEquals(expected, actual);
        verify(tenantLifeCycleService).onboard(request);
        verifyNoMoreInteractions(tenantAuthenticationService, tenantPaymentSettingsService, tenantQueryService);
    }

    @Test
    @DisplayName("updatePaymentSettings() должен делегировать вызов в TenantPaymentSettingsService")
    void updatePaymentSettings_shouldDelegateCorrectly() {
        String tenantId = "tenant_123";
        TenantPaymentSettingsRequest request = mock(TenantPaymentSettingsRequest.class);

        tenantService.updatePaymentSettings(tenantId, request);

        verify(tenantPaymentSettingsService).updatePaymentSettings(tenantId, request);
    }

    @Test
    @DisplayName("getTenant() должен делегировать вызов и вернуть данные тенанта")
    void getTenant_shouldDelegateAndReturnData() {
        String tenantId = "tenant_456";
        TenantOnboardingResponse expected = mock(TenantOnboardingResponse.class);

        when(tenantLifeCycleService.getTenant(tenantId)).thenReturn(expected);

        TenantOnboardingResponse actual = tenantService.getTenant(tenantId);

        assertEquals(expected, actual);
        verify(tenantLifeCycleService).getTenant(tenantId);
    }

    @Test
    @DisplayName("validateTenantAndApiKey() должен успешно делегировать проверку")
    void validateTenantAndApiKey_shouldDelegateSuccessfully() {
        String tenantId = "tenant_789";
        String apiKey = "sk_live_valid_key";

        tenantService.validateTenantAndApiKey(tenantId, apiKey);

        verify(tenantAuthenticationService).validateTenantAndApiKey(tenantId, apiKey);
    }

    @Test
    @DisplayName("validateTenantAndApiKey() должен пробрасывать исключение при невалидных данных")
    void validateTenantAndApiKey_shouldPropagateException() {
        String tenantId = "invalid_tenant";
        String apiKey = "wrong_key";

        doThrow(new IllegalArgumentException("Invalid tenant or API key"))
                .when(tenantAuthenticationService).validateTenantAndApiKey(anyString(), anyString());

        assertThrows(IllegalArgumentException.class, () ->
                tenantService.validateTenantAndApiKey(tenantId, apiKey));
    }

    @Test
    @DisplayName("findTenantIdByYooKassaShopId() должен вернуть Optional с tenantId, если магазин найден")
    void findTenantIdByYooKassaShopId_whenExists_shouldReturnPresent() {
        String shopId = "shop_12345";
        String expectedTenantId = "tenant_abc";

        when(tenantQueryService.findTenantIdByYooKassaShopId(shopId))
                .thenReturn(Optional.of(expectedTenantId));

        Optional<String> result = tenantService.findTenantIdByYooKassaShopId(shopId);

        assertTrue(result.isPresent());
        assertEquals(expectedTenantId, result.get());
        verify(tenantQueryService).findTenantIdByYooKassaShopId(shopId);
    }

    @Test
    @DisplayName("findTenantIdByYooKassaShopId() должен вернуть пустой Optional, если магазин не найден")
    void findTenantIdByYooKassaShopId_whenNotExists_shouldReturnEmpty() {
        String shopId = "unknown_shop";

        when(tenantQueryService.findTenantIdByYooKassaShopId(shopId))
                .thenReturn(Optional.empty());

        Optional<String> result = tenantService.findTenantIdByYooKassaShopId(shopId);

        assertTrue(result.isEmpty());
        verify(tenantQueryService).findTenantIdByYooKassaShopId(shopId);
    }
}