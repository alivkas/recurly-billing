package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.response.WebhookEventResponse;
import ru.nocode.recurlybilling.data.entities.WebhookEvent;
import ru.nocode.recurlybilling.data.repositories.WebhookEventRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookEventServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookEventService webhookEventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void logIncomingWebhookShouldSaveEventWithReceivedStatus() {
        String tenantId = "moscow_digital_school";
        String eventType = "payment.succeeded";
        String paymentId = "pay_123";
        String signature = "a1b2c3d4e5f6...";
        String rawBody = "{\"event\":\"payment.succeeded\",\"object\":{\"payment\":{\"id\":\"pay_123\"}}}";
        Object parsedBody = new Object();

        WebhookEvent savedEvent = new WebhookEvent();
        savedEvent.setId(UUID.randomUUID());
        savedEvent.setTenantId(tenantId);
        savedEvent.setEventType(eventType);
        savedEvent.setPaymentId(paymentId);
        savedEvent.setSignature(signature);
        savedEvent.setRawRequestBody(rawBody);
        savedEvent.setStatus("received");
        savedEvent.setCreatedAt(java.time.LocalDateTime.now());

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(savedEvent);

        WebhookEventResponse response = webhookEventService.logIncomingWebhook(
                tenantId, eventType, paymentId, signature, rawBody, parsedBody
        );

        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getEventType()).isEqualTo(eventType);
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo("received");

        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    }

    @Test
    void updateWebhookStatusShouldUpdateStatusAndError() {
        UUID eventId = UUID.randomUUID();
        WebhookEvent existingEvent = new WebhookEvent();
        existingEvent.setId(eventId);
        existingEvent.setStatus("received");

        when(webhookEventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookEventService.updateWebhookStatus(eventId, "processed", "Payment processed successfully");

        assertThat(existingEvent.getStatus()).isEqualTo("processed");
        assertThat(existingEvent.getProcessingError()).isEqualTo("Payment processed successfully");

        verify(webhookEventRepository, times(1)).findById(eventId);
        verify(webhookEventRepository, times(1)).save(existingEvent);
    }

    @Test
    void getWebhookEventsByTenantShouldReturnListOfResponses() {
        String tenantId = "moscow_digital_school";
        WebhookEvent event1 = createWebhookEvent(tenantId, "payment.succeeded", "pay_123");
        WebhookEvent event2 = createWebhookEvent(tenantId, "payment.canceled", "pay_456");

        when(webhookEventRepository.findByTenantId(tenantId))
                .thenReturn(Arrays.asList(event1, event2));

        List<WebhookEventResponse> responses = webhookEventService.getWebhookEventsByTenant(tenantId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getTenantId()).isEqualTo(tenantId);
        assertThat(responses.get(0).getEventType()).isEqualTo("payment.succeeded");
        assertThat(responses.get(1).getEventType()).isEqualTo("payment.canceled");
    }

    @Test
    void countWebhooksByTypeShouldReturnCorrectCount() {
        String tenantId = "moscow_digital_school";
        String eventType = "payment.succeeded";

        when(webhookEventRepository.countByTenantIdAndEventType(tenantId, eventType))
                .thenReturn(5L);

        long count = webhookEventService.countWebhooksByType(tenantId, eventType);

        assertThat(count).isEqualTo(5L);
    }

    private WebhookEvent createWebhookEvent(String tenantId, String eventType, String paymentId) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setPaymentId(paymentId);
        event.setStatus("processed");
        event.setCreatedAt(java.time.LocalDateTime.now());
        return event;
    }
}