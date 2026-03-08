package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.data.dto.response.ChurnRateResponse;
import ru.nocode.recurlybilling.data.dto.response.PlanAnalyticsResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void calculateMRRShouldReturnCorrectValuesForMixedSubscriptions() {
        String tenantId = "moscow_digital_school";

        Subscription monthlySub = createSubscription(tenantId, UUID.randomUUID(), "active");
        Subscription yearlySub = createSubscription(tenantId, UUID.randomUUID(), "active");
        Subscription semesterSub = createSubscription(tenantId, UUID.randomUUID(), "active");

        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active"))
                .thenReturn(Arrays.asList(monthlySub, yearlySub, semesterSub));

        Plan monthlyPlan = createPlan(UUID.fromString(monthlySub.getPlanId().toString()), "month", 100000L);
        Plan yearlyPlan = createPlan(UUID.fromString(yearlySub.getPlanId().toString()), "year", 1000000L);
        Plan semesterPlan = createPlan(UUID.fromString(semesterSub.getPlanId().toString()), "semester", 400000L);

        when(planRepository.findById(monthlySub.getPlanId())).thenReturn(Optional.of(monthlyPlan));
        when(planRepository.findById(yearlySub.getPlanId())).thenReturn(Optional.of(yearlyPlan));
        when(planRepository.findById(semesterSub.getPlanId())).thenReturn(Optional.of(semesterPlan));

        AnalyticsResponse response = analyticsService.calculateMRR(tenantId);

        assertThat(response.getMrrCents()).isEqualTo(1100000L);
        assertThat(response.getSemesterRevenueCents()).isEqualTo(400000L);
        assertThat(response.getActiveRecurringSubscriptions()).isEqualTo(2);
        assertThat(response.getActiveSemesterSubscriptions()).isEqualTo(1);
        assertThat(response.getTotalActiveSubscriptions()).isEqualTo(3);
    }

    @Test
    void calculateChurnRateShouldReturnCorrectPercentage() {
        String tenantId = "moscow_digital_school";

        when(subscriptionRepository.countActiveSubscriptionsAtDate(eq(tenantId), any(LocalDate.class)))
                .thenReturn(100L);
        when(subscriptionRepository.countCancelledSubscriptionsInPeriod(
                eq(tenantId),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(5L);

        ChurnRateResponse response = analyticsService.calculateChurnRate(tenantId);

        assertThat(response.getChurnRate()).isEqualTo(0.05);
        assertThat(response.getChurnRatePercent()).isEqualTo(5.0);
        assertThat(response.getCancelledSubscriptions()).isEqualTo(5L);
        assertThat(response.getActiveSubscriptionsAtStart()).isEqualTo(100L);
    }

    @Test
    void getPlanAnalyticsShouldReturnCorrectStats() {
        String tenantId = "moscow_digital_school";

        Plan plan1 = createPlan(UUID.randomUUID(), "month", 100000L);
        Plan plan2 = createPlan(UUID.randomUUID(), "semester", 400000L);

        Subscription sub1 = createSubscription(tenantId, plan1.getId(), "active");
        Subscription sub2 = createSubscription(tenantId, plan2.getId(), "active");
        Subscription sub3 = createSubscription(tenantId, plan2.getId(), "cancelled");

        when(subscriptionRepository.findByTenantId(tenantId))
                .thenReturn(Arrays.asList(sub1, sub2, sub3));
        when(planRepository.findByTenantId(tenantId))
                .thenReturn(Arrays.asList(plan1, plan2));
        when(planRepository.findById(plan1.getId())).thenReturn(Optional.of(plan1));
        when(planRepository.findById(plan2.getId())).thenReturn(Optional.of(plan2));

        PlanAnalyticsResponse response = analyticsService.getPlanAnalytics(tenantId);

        assertThat(response.getPlans()).hasSize(2);

        var plan1Stats = response.getPlans().stream()
                .filter(p -> p.getPlanId().equals(plan1.getId().toString()))
                .findFirst().get();
        assertThat(plan1Stats.getActiveSubscriptions()).isEqualTo(1);
        assertThat(plan1Stats.getTotalSubscriptions()).isEqualTo(1);
        assertThat(plan1Stats.getRevenueCents()).isEqualTo(100000L);

        var plan2Stats = response.getPlans().stream()
                .filter(p -> p.getPlanId().equals(plan2.getId().toString()))
                .findFirst().get();
        assertThat(plan2Stats.getActiveSubscriptions()).isEqualTo(1);
        assertThat(plan2Stats.getCancelledSubscriptions()).isEqualTo(1);
        assertThat(plan2Stats.getTotalSubscriptions()).isEqualTo(2);
        assertThat(plan2Stats.getRevenueCents()).isEqualTo(400000L);
    }

    private Subscription createSubscription(String tenantId, UUID planId, String status) {
        Subscription sub = new Subscription();
        sub.setId(UUID.randomUUID());
        sub.setTenantId(tenantId);
        sub.setPlanId(planId);
        sub.setStatus(status);
        sub.setCreatedAt(java.time.LocalDateTime.now());
        if ("cancelled".equals(status)) {
            sub.setCancelAt(LocalDate.now());
        }
        return sub;
    }

    private Plan createPlan(UUID id, String interval, long priceCents) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode("test-" + interval);
        plan.setName("Test Plan");
        plan.setInterval(interval);
        plan.setPriceCents(priceCents);
        return plan;
    }
}