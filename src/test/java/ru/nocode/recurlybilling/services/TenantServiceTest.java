package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void onboardShouldGenerateApiKeyAndSaveTenant() {
        var request = new TenantOnboardingRequest("Moscow Digital School", "admin@mds.ru");
        when(tenantRepository.existsByTenantId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_key_123");

        TenantOnboardingResponse response = tenantService.onboard(request);

        assertThat(response.getTenantId()).isEqualTo("moscow_digital_school");
        assertThat(response.getApiKey()).startsWith("sk_live_");
        assertThat(response.getCreatedAt()).isNotNull();
    }
}