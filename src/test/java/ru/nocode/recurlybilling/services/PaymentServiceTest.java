package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private Environment environment;

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
        when(environment.getProperty(anyString(), anyString())).thenReturn("https://default.example.com/success");

        // when
        PaymentResponse response = paymentService.createPaymentForSubscription(subscription);

        // then
        assertThat(response.paymentId()).isEqualTo("pay_123");
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.confirmationUrl()).isNotNull();
        assertThat(response.amountCents()).isEqualTo(100000L);

        verify(invoiceRepository, times(2)).save(any(Invoice.class));
        verify(yooKassaClient, times(1)).createPayment(any(YooKassaPaymentRequest.class));
    }

    @Test
    void createPaymentWithInvalidPaymentMethodShouldThrowException() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setPaymentMethod("invalid_method"); // ← невалидный метод

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId("moscow_digital_school");
        plan.setCode("math-autumn-2025");
        plan.setPriceCents(100000L);

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // when/then
        assertThatThrownBy(() -> paymentService.createPaymentForSubscription(subscription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid_method");
    }

    @Test
    void handleYooKassaWebhookShouldUpdateInvoiceStatusAndExtendSubscription() {
        // given
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

        when(invoiceRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        paymentService.handleYooKassaWebhook(paymentId, status, Map.of("event", "payment.succeeded"));

        // then
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        assertThat(invoice.getStatus()).isEqualTo("paid");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void handleYooKassaWebhookForOneTimeSubscriptionShouldNotExtend() {
        // given
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
        invoice.setAmountCents(400000L);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setStatus("active");
        subscription.setCurrentPeriodStart(LocalDate.of(2025, 9, 1));
        subscription.setCurrentPeriodEnd(LocalDate.of(2025, 12, 31));
        subscription.setNextBillingDate(null); // ← разовая оплата

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId("moscow_digital_school");
        plan.setInterval("semester");

        when(invoiceRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(invoice));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        paymentService.handleYooKassaWebhook(paymentId, status, Map.of("event", "payment.succeeded"));

        // then
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(subscriptionRepository, never()).save(any(Subscription.class)); // ← не продлеваем
        assertThat(invoice.getStatus()).isEqualTo("paid");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void handleYooKassaWebhookWithDuplicateStatusShouldNotExtendAgain() {
        // given
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
        invoice.setStatus("paid"); // ← уже оплачен
        invoice.setAmountCents(100000L);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId("moscow_digital_school");
        subscription.setPlanId(planId);
        subscription.setStatus("active");
        subscription.setCurrentPeriodStart(LocalDate.of(2025, 1, 1));
        subscription.setCurrentPeriodEnd(LocalDate.of(2025, 1, 31));
        subscription.setNextBillingDate(LocalDate.of(2025, 2, 1));

        when(invoiceRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        paymentService.handleYooKassaWebhook(paymentId, status, Map.of("event", "payment.succeeded"));

        // then
        verify(subscriptionRepository, never()).save(any(Subscription.class)); // ← не продлеваем повторно
        assertThat(invoice.getStatus()).isEqualTo("paid");
    }
}