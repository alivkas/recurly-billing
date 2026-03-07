package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createSubscriptionWithSemesterPlanShouldSetNextBillingDateToNull() {
        // given
        String tenantId = "moscow_digital_school";
        UUID planId = UUID.randomUUID();
        String planIdStr = planId.toString();

        var request = new SubscriptionCreateRequest(
                "user_12345",
                planIdStr,
                LocalDate.of(2025, 9, 1),
                "bank_card"
        );

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setExternalId("user_12345");

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId(tenantId);
        plan.setInterval("semester");
        plan.setPriceCents(400000L);
        plan.setEndDate(LocalDate.of(2025, 12, 31));

        when(customerRepository.findByTenantIdAndExternalId(tenantId, "user_12345"))
                .thenReturn(Optional.of(customer));
        when(planRepository.findByIdAndTenantId(planId, tenantId))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByTenantIdAndCustomerExternalId(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse mockPaymentResponse = new PaymentResponse(
                "pay_123",
                "pending",
                "https://yoomoney.ru/checkout",
                400000L,
                "RUB",
                java.time.LocalDateTime.now()
        );
        when(paymentService.createPaymentForSubscription(any(Subscription.class)))
                .thenReturn(mockPaymentResponse);

        // when
        SubscriptionResponse response = subscriptionService.createSubscription(tenantId, request);

        // then
        assertThat(response.nextBillingDate()).isNull();
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(response.status()).isEqualTo("active");
    }

    @Test
    void createSubscriptionWithDuplicateShouldThrowException() {
        // given
        String tenantId = "moscow_digital_school";
        UUID planId = UUID.randomUUID();
        String planIdStr = planId.toString();

        var request = new SubscriptionCreateRequest(
                "user_12345",
                planIdStr,
                LocalDate.of(2025, 1, 1),
                "bank_card"
        );

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setExternalId("user_12345");

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId(tenantId);
        plan.setInterval("month");
        plan.setIntervalCount(1);
        plan.setPriceCents(100000L);

        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setPlanId(planId);
        existing.setStatus("active");

        when(customerRepository.findByTenantIdAndExternalId(tenantId, "user_12345"))
                .thenReturn(Optional.of(customer));
        when(planRepository.findByIdAndTenantId(planId, tenantId))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByTenantIdAndCustomerExternalId(anyString(), anyString()))
                .thenReturn(List.of(existing));

        // when/then
        assertThatThrownBy(() -> subscriptionService.createSubscription(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void cancelSubscriptionWithCancelAtEndOfPeriodShouldSetCancelAtToPeriodEnd() {
        // given
        String tenantId = "moscow_digital_school";
        String subscriptionId = UUID.randomUUID().toString();

        Subscription subscription = new Subscription();
        subscription.setId(UUID.fromString(subscriptionId));
        subscription.setTenantId(tenantId);
        subscription.setStatus("active");
        subscription.setCurrentPeriodEnd(LocalDate.of(2025, 12, 31));

        when(subscriptionRepository.findByIdAndTenantId(any(UUID.class), eq(tenantId)))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var cancelRequest = new SubscriptionCancelRequest(false);

        // when
        SubscriptionResponse response = subscriptionService.cancelSubscription(tenantId, subscriptionId, cancelRequest);

        // then
        assertThat(response.status()).isEqualTo("cancelled");
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void handlePaymentFailedShouldSetStatusToPastDue() {
        // given
        UUID subscriptionId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setStatus("active");

        Invoice failedInvoice = new Invoice();
        failedInvoice.setId(UUID.randomUUID());
        failedInvoice.setStatus("failed");

        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(subscription));
        when(invoiceRepository.findBySubscriptionIdAndStatusOrderByCreatedAtDesc(subscriptionId, "failed"))
                .thenReturn(List.of(failedInvoice));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        subscriptionService.handlePaymentFailed(subscriptionId);

        // then
        assertThat(subscription.getStatus()).isEqualTo("past_due");
        verify(subscriptionRepository, times(1)).save(subscription);
    }

    @Test
    void processBillingForTenantShouldCreatePaymentsForDueSubscriptions() {
        // given
        String tenantId = "moscow_digital_school";
        UUID subscriptionId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setTenantId(tenantId);
        subscription.setStatus("active");
        subscription.setNextBillingDate(LocalDate.now());

        when(subscriptionRepository.findByTenantIdAndStatusAndNextBillingDateBefore(
                eq(tenantId), eq("active"), any(LocalDate.class)))
                .thenReturn(List.of(subscription));

        PaymentResponse mockPaymentResponse = new PaymentResponse(
                "pay_123",
                "pending",
                "https://yoomoney.ru/checkout",
                100000L,
                "RUB",
                java.time.LocalDateTime.now()
        );
        when(paymentService.createPaymentForSubscription(any(Subscription.class)))
                .thenReturn(mockPaymentResponse);

        // when
        subscriptionService.processBillingForTenant(tenantId);

        // then
        verify(paymentService, times(1)).createPaymentForSubscription(subscription);
    }
}