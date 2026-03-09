package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String tenantId) {
        LocalDate now = LocalDate.now();
        LocalDate thirtyDaysAgo = now.minusDays(30);
        LocalDate lastMonth = now.minusMonths(1);

        List<Subscription> activeSubs = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, "active");
        Long activeSubscriptions = (long) activeSubs.size();
        Long totalCustomers = customerRepository.countByTenantId(tenantId);
        BigDecimal mrr = calculateMRR(activeSubs);
        BigDecimal churnRate = calculateChurnRate(tenantId, now);

        Map<String, Long> subscriptionsByPlan = activeSubs.stream()
                .collect(Collectors.groupingBy(
                        sub -> sub.getPlanId().toString(),
                        Collectors.counting()
                ));

        Map<LocalDate, BigDecimal> revenueByMonth = getRevenueByMonth(tenantId, now);

        BigDecimal previousMRR = calculatePreviousMRR(tenantId, lastMonth);
        BigDecimal mrrGrowthRate = calculateGrowthRate(previousMRR, mrr);

        Long newSubscriptionsLast30Days = subscriptionRepository
                .countByTenantIdAndCreatedAtAfter(tenantId, thirtyDaysAgo.atStartOfDay());

        return new AnalyticsResponse(
                mrr,
                activeSubscriptions,
                totalCustomers,
                churnRate,
                subscriptionsByPlan,
                revenueByMonth,
                mrrGrowthRate,
                newSubscriptionsLast30Days
        );
    }

    private BigDecimal calculateMRR(List<Subscription> activeSubs) {
        return activeSubs.stream()
                .map(sub -> {
                    String interval = sub.getInterval();
                    Long amountCents = sub.getAmountCents();

                    if (amountCents == null || amountCents == 0) {
                        return BigDecimal.ZERO;
                    }

                    if ("semester".equals(interval)) {
                        return BigDecimal.valueOf(amountCents / 6.0 / 100.0);
                    } else if ("year".equals(interval)) {
                        return BigDecimal.valueOf(amountCents / 12.0 / 100.0);
                    } else {
                        // month, custom → считаем как месячный
                        return BigDecimal.valueOf(amountCents / 100.0);
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateChurnRate(String tenantId, LocalDate now) {
        LocalDate oneMonthAgo = now.minusMonths(1);

        Long activeLastMonth = subscriptionRepository.countActiveAtDate(tenantId, oneMonthAgo);
        Long churnedThisMonth = subscriptionRepository.countChurnedInPeriod(
                tenantId, oneMonthAgo, now
        );

        if (activeLastMonth == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf((churnedThisMonth * 100.0) / activeLastMonth)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<LocalDate, BigDecimal> getRevenueByMonth(String tenantId, LocalDate now) {
        Map<LocalDate, BigDecimal> revenue = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            LocalDate month = now.minusMonths(i);
            LocalDateTime start = month.withDayOfMonth(1).atStartOfDay();
            LocalDateTime end = start.plusMonths(1).minusDays(1);

            Long revenueCents = invoiceRepository
                    .findPaidRevenueByTenantAndPeriod(tenantId, start, end)
                    .orElse(0L);

            BigDecimal monthlyRevenue = BigDecimal.valueOf(revenueCents)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            revenue.put(month, monthlyRevenue);
        }
        return revenue;
    }

    private BigDecimal calculatePreviousMRR(String tenantId, LocalDate date) {
        List<Subscription> activeThen = subscriptionRepository
                .findActiveAtDate(tenantId, date);
        return calculateMRR(activeThen);
    }

    private BigDecimal calculateGrowthRate(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ?
                    BigDecimal.ZERO : BigDecimal.valueOf(100.0);
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
