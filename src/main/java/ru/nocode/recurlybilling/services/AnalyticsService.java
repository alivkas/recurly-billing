package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.PlanStats;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.data.dto.response.ChurnRateResponse;
import ru.nocode.recurlybilling.data.dto.response.PlanAnalyticsResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse calculateMRR(String tenantId) {
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, "active");

        long totalMRR = 0L;
        long semesterRevenue = 0L;
        int activeRecurringSubscriptions = 0;
        int activeSemesterSubscriptions = 0;

        for (Subscription subscription : activeSubscriptions) {
            Plan plan = planRepository.findById(subscription.getPlanId())
                    .orElse(null);

            if (plan == null) continue;

            if (isRecurringPlan(plan)) {
                totalMRR += plan.getPriceCents();
                activeRecurringSubscriptions++;
            } else {
                semesterRevenue += plan.getPriceCents();
                activeSemesterSubscriptions++;
            }
        }

        return new AnalyticsResponse(
                tenantId,
                totalMRR,
                totalMRR / 100.0,
                semesterRevenue,
                semesterRevenue / 100.0,
                activeRecurringSubscriptions,
                activeSemesterSubscriptions,
                activeRecurringSubscriptions + activeSemesterSubscriptions,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public ChurnRateResponse calculateChurnRate(String tenantId) {
        LocalDate now = LocalDate.now();
        LocalDate oneMonthAgo = now.minusMonths(1);
        LocalDate twoMonthsAgo = now.minusMonths(2);

        long activeAtStart = subscriptionRepository.countActiveSubscriptionsAtDate(tenantId, twoMonthsAgo);
        long cancelledLastMonth = subscriptionRepository.countCancelledSubscriptionsInPeriod(
                tenantId, oneMonthAgo, now
        );

        double churnRate = 0.0;
        if (activeAtStart > 0) {
            churnRate = (double) cancelledLastMonth / activeAtStart;
        }

        return new ChurnRateResponse(
                tenantId,
                churnRate,
                new BigDecimal(churnRate * 100).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                cancelledLastMonth,
                activeAtStart,
                twoMonthsAgo,
                now,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public PlanAnalyticsResponse getPlanAnalytics(String tenantId) {
        List<Subscription> allSubscriptions = subscriptionRepository.findByTenantId(tenantId);
        List<Plan> plans = planRepository.findByTenantId(tenantId);

        Map<String, PlanStats> planStatsMap = new HashMap<>();
        for (Plan plan : plans) {
            planStatsMap.put(
                    plan.getId().toString(),
                    new PlanStats(
                            plan.getId().toString(),
                            plan.getCode(),
                            plan.getName(),
                            plan.getInterval(),
                            plan.getPriceCents(),
                            0, 0, 0, 0L
                    )
            );
        }

        for (Subscription subscription : allSubscriptions) {
            String planIdStr = subscription.getPlanId().toString();
            PlanStats stats = planStatsMap.get(planIdStr);

            if (stats == null) continue;

            stats.setTotalSubscriptions(stats.getTotalSubscriptions() + 1);

            if ("active".equals(subscription.getStatus()) || "trialing".equals(subscription.getStatus())) {
                stats.setActiveSubscriptions(stats.getActiveSubscriptions() + 1);
                stats.setRevenueCents(stats.getRevenueCents() + stats.getPriceCents());
            } else if ("cancelled".equals(subscription.getStatus())) {
                stats.setCancelledSubscriptions(stats.getCancelledSubscriptions() + 1);
            }
        }

        return new PlanAnalyticsResponse(
                tenantId,
                new ArrayList<>(planStatsMap.values()),
                plans.size(),
                plans.stream().mapToLong(Plan::getPriceCents).sum(),
                LocalDateTime.now()
        );
    }

    private boolean isRecurringPlan(Plan plan) {
        return "month".equals(plan.getInterval()) || "year".equals(plan.getInterval());
    }
}
