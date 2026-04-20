package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.nocode.recurlybilling.components.metrics.BusinessMetrics;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaClient;
import ru.nocode.recurlybilling.data.dto.request.YooKassaPaymentRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.YooKassaPaymentResponse;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private YooKassaClient yooKassaClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private Environment environment;
    @Mock private AccessService accessService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private BusinessMetrics businessMetrics;

    @InjectMocks
    private PaymentService paymentService;

    private final String tenantId = "moscow_digital";
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String idempotencyKey = "idem_123";

    @Test
    @DisplayName("createPaymentForSubscription() при статусе 'succeeded' должен вызвать onPaymentSuccess")
    void createPaymentForSubscription_whenPaid_shouldCallOnPaymentSuccess() throws JsonProcessingException {
        Subscription subscription = createSubscription("bank_card", "pm_existing");
        Plan plan = createPlan(29900L, "month", "RUB");
        subscription.setNextBillingDate(LocalDate.now().plusMonths(1));

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });

        YooKassaPaymentResponse yooResponse = createYooKassaResponse("succeeded", null, null);
        when(yooKassaClient.createPayment(any(YooKassaPaymentRequest.class), eq(idempotencyKey)))
                .thenReturn(yooResponse);

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        PaymentResponse response = paymentService.createPaymentForSubscription(subscription, idempotencyKey);

        assertEquals("paid", response.status());
        verify(accessService).grantAccess(eq(tenantId), eq(customerId), anyString(), any(LocalDate.class));
        verify(businessMetrics).recordPaymentSuccess(eq(tenantId), eq(29900L), eq("RUB"));
        verify(auditLogService).logPaymentSuccess(any(), any(), any(), anyLong(), any(), any());
        verify(notificationService).sendPaymentSucceededNotification(any(), any(), any());
    }

    @Test
    @DisplayName("handleYooKassaWebhook() при статусе 'paid' должен продлить подписку и отправить уведомление")
    void handleYooKassaWebhook_whenPaid_shouldExtendSubscriptionAndNotify() {
        Invoice invoice = createInvoice("pending", 29900L);
        Subscription subscription = createSubscription("bank_card", null);
        subscription.setNextBillingDate(LocalDate.now().plusMonths(1));
        Plan plan = createPlan(29900L, "month", "RUB");

        when(invoiceRepository.findByPaymentId("pay_123")).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(List.of(invoice));

        Map<String, Object> webhook = createWebhook("succeeded", Map.of("subscriptionId", subscriptionId.toString()));

        paymentService.handleYooKassaWebhook("pay_123", "succeeded", webhook, tenantId);

        verify(businessMetrics).recordWebhookReceived(tenantId, "payment.succeeded", "paid");
        verify(accessService).grantAccess(eq(tenantId), eq(customerId), anyString(), any(LocalDate.class));
        verify(notificationService).sendPaymentSucceededNotification(subscription, invoice, plan);
        verify(auditLogService).logPaymentSuccess(any(), any(), any(), anyLong(), any(), any());
        assertEquals("paid", invoice.getStatus());
    }

    @Test
    @DisplayName("handleYooKassaWebhook() при pending_deferred → paid должен конвертировать триал")
    void handleYooKassaWebhook_whenTrialConversion_shouldConvertTrial() {
        Invoice invoice = createInvoice("pending_deferred", 29900L);
        Subscription subscription = createSubscription("bank_card", null);
        subscription.setStatus("trialing");
        subscription.setTrialEnd(LocalDate.now().plusDays(3));
        Plan plan = createPlan(29900L, "month", "RUB");

        when(invoiceRepository.findByPaymentId("pay_123")).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        Map<String, Object> webhook = createWebhook("succeeded", Map.of("subscriptionId", subscriptionId.toString()));

        paymentService.handleYooKassaWebhook("pay_123", "succeeded", webhook, tenantId);

        verify(businessMetrics).recordTrialConverted(tenantId, plan.getCode());
        assertEquals("active", subscription.getStatus());
        assertNull(subscription.getTrialEnd()); // триал завершён
        assertNotNull(subscription.getNextBillingDate());
    }

    @Test
    @DisplayName("getPaymentByTenant() должен вернуть данные платежа и проверить tenantId")
    void getPaymentByTenant_shouldReturnPaymentAndValidateTenant() {
        Invoice invoice = createInvoice("paid", 29900L);
        invoice.setPaymentId("pay_123");
        invoice.setConfirmationUrl("https://receipt.url");

        when(invoiceRepository.findByPaymentId("pay_123")).thenReturn(Optional.of(invoice));

        PaymentResponse response = paymentService.getPaymentByTenant("pay_123", tenantId);

        assertEquals("pay_123", response.paymentId());
        assertEquals("paid", response.status());
        assertEquals("https://receipt.url", response.confirmationUrl());
        assertEquals(29900L, response.amountCents());
    }

    @Test
    @DisplayName("getPaymentByTenant() должен выбросить исключение при несовпадении tenantId")
    void getPaymentByTenant_whenTenantMismatch_shouldThrow() {
        Invoice invoice = createInvoice("paid", 29900L);
        invoice.setTenantId("other_tenant");

        when(invoiceRepository.findByPaymentId("pay_123")).thenReturn(Optional.of(invoice));

        assertThrows(IllegalStateException.class, () ->
                paymentService.getPaymentByTenant("pay_123", tenantId));
    }

    @Test
    @DisplayName("getLastPaymentForSubscription() должен вернуть последний инвойс")
    void getLastPaymentForSubscription_shouldReturnLatestInvoice() {
        Invoice oldInvoice = createInvoice("paid", 19900L);
        oldInvoice.setCreatedAt(LocalDateTime.now().minusDays(10));
        Invoice newInvoice = createInvoice("paid", 29900L);
        newInvoice.setCreatedAt(LocalDateTime.now());

        when(invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(List.of(newInvoice, oldInvoice));

        PaymentResponse response = paymentService.getLastPaymentForSubscription(subscriptionId);

        assertEquals(29900L, response.amountCents()); // последний по дате
        verify(invoiceRepository).findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
    }

    @Test
    @DisplayName("getLastPaymentForSubscription() должен вернуть null, если инвойсов нет")
    void getLastPaymentForSubscription_whenNoInvoices_shouldReturnNull() {
        when(invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(List.of());

        PaymentResponse response = paymentService.getLastPaymentForSubscription(subscriptionId);

        assertNull(response);
    }

    // ==================== HELPER METHODS ====================

    private Subscription createSubscription(String paymentMethod, String paymentMethodId) {
        Subscription sub = new Subscription();
        sub.setId(subscriptionId);
        sub.setTenantId(tenantId);
        sub.setCustomerId(customerId);
        sub.setPlanId(planId);
        sub.setPaymentMethod(paymentMethod);
        sub.setPaymentMethodId(paymentMethodId);
        sub.setCurrentPeriodEnd(LocalDate.now().plusMonths(1));
        return sub;
    }

    private Plan createPlan(Long priceCents, String interval, String currency) {
        Plan plan = new Plan();
        plan.setId(planId);
        plan.setPriceCents(priceCents);
        plan.setInterval(interval);
        plan.setCurrency(currency);
        plan.setCode("test_plan");
        return plan;
    }

    private Invoice createInvoice(String status, Long amountCents) {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantId);
        invoice.setSubscriptionId(subscriptionId);
        invoice.setStatus(status);
        invoice.setAmountCents(amountCents);
        invoice.setCurrency("RUB");
        invoice.setCreatedAt(LocalDateTime.now());
        return invoice;
    }

    private YooKassaPaymentResponse createYooKassaResponse(String status, String pmId, String confirmationUrl) {
        YooKassaPaymentResponse response = mock(YooKassaPaymentResponse.class);
        when(response.getId()).thenReturn("pay_123");
        when(response.getStatus()).thenReturn(status);
        when(response.getCreatedAt()).thenReturn(LocalDateTime.now());

        if (pmId != null) {
            YooKassaPaymentResponse.PaymentMethod pm = mock(YooKassaPaymentResponse.PaymentMethod.class);
            when(pm.getId()).thenReturn(pmId);
            when(response.getPaymentMethod()).thenReturn(pm);
        }

        if (confirmationUrl != null) {
            YooKassaPaymentResponse.Confirmation conf = mock(YooKassaPaymentResponse.Confirmation.class);
            when(conf.getType()).thenReturn("redirect");
            when(conf.getConfirmationUrl()).thenReturn(confirmationUrl);
            when(response.getConfirmation()).thenReturn(conf);
        }

        return response;
    }

    private Map<String, Object> createWebhook(String status, Map<String, Object> metadata) {
        Map<String, Object> payment = new HashMap<>();
        payment.put("id", "pay_123");
        payment.put("status", status);
        payment.put("metadata", metadata);
        return Map.of("object", payment);
    }

    private JsonNode createErrorJsonNode(String description) throws JsonProcessingException {
        JsonNode root = mock(JsonNode.class);
        JsonNode errorNode = mock(JsonNode.class);
        JsonNode descNode = mock(JsonNode.class);

        when(root.has("error")).thenReturn(true);
        when(root.get("error")).thenReturn(errorNode);
        when(errorNode.has("description")).thenReturn(true);
        when(errorNode.get("description")).thenReturn(descNode);
        when(descNode.asText()).thenReturn(description);

        return root;
    }
}