package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaClient;
import ru.nocode.recurlybilling.data.dto.request.PaymentCreateRequest;
import ru.nocode.recurlybilling.data.dto.request.YooKassaPaymentRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.YooKassaPaymentResponse;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private YooKassaClient yooKassaClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createPaymentShouldCreateInvoiceAndCallYooKassa() {
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        var request = new PaymentCreateRequest(
                subscriptionId.toString(),
                100000L,
                "bank_card",
                "https://example.com/success",
                "Оплата курса",
                new HashMap<>()
        );

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setPaymentMethod("bank_card");

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId("moscow_digital_school");
        plan.setCode("math-autumn-2025");
        plan.setPriceCents(100000L);

        Invoice invoice1 = new Invoice();
        invoice1.setId(UUID.randomUUID());
        invoice1.setTenantId("moscow_digital_school");
        invoice1.setSubscriptionId(subscriptionId);
        invoice1.setAmountCents(100000L);
        invoice1.setStatus("pending");
        invoice1.setPaymentMethod("bank_card");
        invoice1.setAttemptCount(0);
        invoice1.setCreatedAt(LocalDateTime.now());

        Invoice invoice2 = new Invoice();
        invoice2.setId(invoice1.getId());
        invoice2.setTenantId("moscow_digital_school");
        invoice2.setSubscriptionId(subscriptionId);
        invoice2.setAmountCents(100000L);
        invoice2.setStatus("pending");
        invoice2.setPaymentId("pay_123");
        invoice2.setPaymentMethod("bank_card");
        invoice2.setAttemptCount(0);
        invoice2.setCreatedAt(LocalDateTime.now());
        invoice2.setUpdatedAt(LocalDateTime.now());

        YooKassaPaymentResponse yooResponse = new YooKassaPaymentResponse();
        yooResponse.setId("pay_123");
        yooResponse.setStatus("waiting_for_capture");

        YooKassaPaymentResponse.Confirmation confirmation = new YooKassaPaymentResponse.Confirmation();
        confirmation.setConfirmationUrl("https://yoomoney.ru/checkout/payments/v2/confirm");
        yooResponse.setConfirmation(confirmation);

        YooKassaPaymentResponse.Amount amount = new YooKassaPaymentResponse.Amount();
        amount.setValue("1000.00");
        amount.setCurrency("RUB");
        yooResponse.setAmount(amount);
        yooResponse.setCreatedAt(LocalDateTime.now());

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice input = inv.getArgument(0);
            return input.getPaymentId() == null ? invoice1 : invoice2;
        });
        when(yooKassaClient.createPayment(any(YooKassaPaymentRequest.class))).thenReturn(yooResponse);

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.paymentId()).isEqualTo("pay_123");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.confirmationUrl()).isNotNull();
        assertThat(response.amountCents()).isEqualTo(100000L);

        verify(invoiceRepository, times(2)).save(any(Invoice.class));
        verify(yooKassaClient, times(1)).createPayment(any(YooKassaPaymentRequest.class));
    }

    @Test
    void handleYooKassaWebhookShouldUpdateInvoiceStatusAndExtendSubscription() {
        String paymentId = "pay_123";
        String status = "succeeded";
        UUID invoiceId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTenantId("moscow_digital_school");
        invoice.setSubscriptionId(subscriptionId);
        invoice.setPaymentId(paymentId);
        invoice.setStatus("pending");
        invoice.setAmountCents(100000L);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setStatus("active");
        subscription.setCurrentPeriodStart(LocalDate.of(2025, 1, 1));
        subscription.setCurrentPeriodEnd(LocalDate.of(2025, 1, 31));
        subscription.setNextBillingDate(LocalDate.of(2025, 2, 1));

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId("moscow_digital_school");
        plan.setInterval("month");
        plan.setIntervalCount(1);

        when(objectMapper.valueToTree(any(Map.class))).thenReturn(null);
        when(invoiceRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleYooKassaWebhook(paymentId, status, Map.of("event", "payment.succeeded"));

        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertThat(invoice.getStatus()).isEqualTo("paid");

        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void retryFailedPaymentShouldIncrementAttemptCountAndCreateNewPayment() {
        UUID invoiceId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTenantId("moscow_digital_school");
        invoice.setSubscriptionId(subscriptionId);
        invoice.setStatus("failed");
        invoice.setAttemptCount(1);
        invoice.setAmountCents(100000L);
        invoice.setPaymentMethod("bank_card");

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setPaymentMethod("bank_card");

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId("moscow_digital_school");
        plan.setPriceCents(100000L);

        YooKassaPaymentResponse yooResponse = new YooKassaPaymentResponse();
        yooResponse.setId("pay_retry_123");
        yooResponse.setStatus("waiting_for_capture");
        yooResponse.setCreatedAt(LocalDateTime.now());

        YooKassaPaymentResponse.Amount amount = new YooKassaPaymentResponse.Amount();
        amount.setValue("1000.00");
        amount.setCurrency("RUB");
        yooResponse.setAmount(amount);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(yooKassaClient.createPayment(any(YooKassaPaymentRequest.class))).thenReturn(yooResponse);

        paymentService.retryFailedPayment(invoiceId);

        assertThat(invoice.getAttemptCount()).isEqualTo(2);
        assertThat(invoice.getStatus()).isEqualTo("pending");
        assertThat(invoice.getPaymentId()).isEqualTo("pay_retry_123");

        verify(yooKassaClient, times(1)).createPayment(any(YooKassaPaymentRequest.class));
    }
}