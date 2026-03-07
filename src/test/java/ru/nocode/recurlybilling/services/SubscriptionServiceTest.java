package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createSubscriptionWithSemesterPlanShouldSetNextBillingDateToNull() {
        String tenantId = "moscow_digital_school";

        UUID planId = UUID.randomUUID();
        String planIdStr = planId.toString();

        var request = new SubscriptionCreateRequest("user_12345", planIdStr, LocalDate.of(2025, 9, 1));

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setExternalId("user_12345");

        Plan plan = new Plan();
        plan.setId(planId);
        plan.setTenantId(tenantId);
        plan.setInterval("semester");
        plan.setPriceCents(400000L);

        when(customerRepository.findByTenantIdAndExternalId(tenantId, "user_12345"))
                .thenReturn(Optional.of(customer));
        when(planRepository.findByIdAndTenantId(planId, tenantId))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByTenantIdAndCustomerExternalId(anyString(), anyString()))
                .thenReturn(java.util.Collections.emptyList());
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.createPaymentForSubscription(any(Subscription.class)))
                .thenReturn(new Invoice());

        SubscriptionResponse response = subscriptionService.createSubscription(tenantId, request);

        assertThat(response.nextBillingDate()).isNull();
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(response.status()).isEqualTo("active");
    }

    @Test
    void createSubscriptionWithMonthlyPlanShouldSetNextBillingDate() {
        // given
        String tenantId = "moscow_digital_school";

        UUID planId = UUID.randomUUID();
        String planIdStr = planId.toString();

        var request = new SubscriptionCreateRequest("user_12345", planIdStr, LocalDate.of(2025, 1, 1));

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

        when(customerRepository.findByTenantIdAndExternalId(tenantId, "user_12345"))
                .thenReturn(Optional.of(customer));
        when(planRepository.findByIdAndTenantId(planId, tenantId))
                .thenReturn(Optional.of(plan));
        when(subscriptionRepository.findByTenantIdAndCustomerExternalId(anyString(), anyString()))
                .thenReturn(java.util.Collections.emptyList());
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> {
                    Subscription saved = inv.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(paymentService.createPaymentForSubscription(any(Subscription.class)))
                .thenReturn(new Invoice());

        SubscriptionResponse response = subscriptionService.createSubscription(tenantId, request);

        assertThat(response.nextBillingDate()).isEqualTo(LocalDate.of(2025, 2, 2));
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2025, 2, 1));
    }

    @Test
    void cancelSubscriptionWithCancelAtEndOfPeriodShouldSetCancelAtToPeriodEnd() {
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

        SubscriptionResponse response = subscriptionService.cancelSubscription(tenantId, subscriptionId, cancelRequest);

        assertThat(response.status()).isEqualTo("cancelled");
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}