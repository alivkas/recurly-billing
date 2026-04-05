package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PlanRepository planRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int CACHE_TTL_SECONDS = 900;

    @Cacheable(value = "analytics", key = "#tenantId")
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String tenantId) {
        LocalDate now = LocalDate.now();

        PeriodInfo period = buildPeriodInfo(now);
        LocalDateTime currentStart = LocalDate.parse(period.start).atStartOfDay();
        LocalDateTime currentEnd = LocalDate.parse(period.end).atTime(23, 59, 59);
        LocalDateTime previousStart = LocalDate.parse(period.previousStart).atStartOfDay();
        LocalDateTime previousEnd = LocalDate.parse(period.previousEnd).atTime(23, 59, 59);

        RevenueMetrics revenue = buildRevenueMetrics(tenantId, currentStart, currentEnd, previousStart, previousEnd);
        SubscriptionMetrics subscriptions = buildSubscriptionMetrics(tenantId, currentStart, currentEnd);
        CustomerMetrics customers = buildCustomerMetrics(tenantId, currentStart, currentEnd);
        PaymentMetrics payments = buildPaymentMetrics(tenantId, currentStart, currentEnd);

        return new AnalyticsResponse(
                period,
                revenue,
                subscriptions,
                customers,
                payments,
                LocalDateTime.now(),
                CACHE_TTL_SECONDS
        );
    }

    private PeriodInfo buildPeriodInfo(LocalDate now) {
        LocalDate currentStart = now.withDayOfMonth(1);
        LocalDate currentEnd = now.withDayOfMonth(now.lengthOfMonth());
        LocalDate prevMonth = now.minusMonths(1);
        LocalDate previousStart = prevMonth.withDayOfMonth(1);
        LocalDate previousEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());

        return new PeriodInfo(
                currentStart.format(DATE_FORMAT),
                currentEnd.format(DATE_FORMAT),
                previousStart.format(DATE_FORMAT),
                previousEnd.format(DATE_FORMAT)
        );
    }

    private RevenueMetrics buildRevenueMetrics(String tenantId,
                                               LocalDateTime currentStart, LocalDateTime currentEnd,
                                               LocalDateTime previousStart, LocalDateTime previousEnd) {
        List<Subscription> activeSubs = subscriptionRepository.findByTenantIdAndStatus(tenantId, "active");
        BigDecimal mrrCents = calculateMrrInCents(activeSubs);
        Long mrr = mrrCents.longValue();
        Long arr = mrr * 12;
        Long totalRevenueCents = invoiceRepository
                .findPaidRevenueByPaidAtPeriod(tenantId, currentStart, currentEnd)
                .orElse(0L);
        Long totalRevenue = totalRevenueCents;
        Long successfulPayments = countInvoicesByStatus(tenantId, currentStart, currentEnd, "paid");
        Long averageCheck = successfulPayments > 0 ? totalRevenue / successfulPayments : 0;

        List<Subscription> activeSubsPrevious = subscriptionRepository.findActiveAtDate(tenantId, previousStart.toLocalDate());
        BigDecimal previousMrrCents = calculateMrrInCents(activeSubsPrevious);
        Double growth = calculateGrowthRate(previousMrrCents, mrrCents);
        List<TimeseriesPoint> timeseries = buildDailyRevenueTimeseries(tenantId, currentStart, currentEnd);
        String currency = "RUB";

        return new RevenueMetrics(
                mrr, arr, totalRevenue, averageCheck, growth, currency, timeseries
        );
    }

    private BigDecimal calculateMrrInCents(List<Subscription> subs) {
        return subs.stream()
                .map(sub -> {
                    Long amount = sub.getAmountCents();
                    if (amount == null || amount == 0) return BigDecimal.ZERO;

                    String interval = sub.getInterval();
                    return switch (interval != null ? interval : "month") {
                        case "year" -> BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(12), RoundingMode.HALF_UP);
                        case "semester" -> BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(6), RoundingMode.HALF_UP);
                        case "month", "weekly", "daily" -> BigDecimal.valueOf(amount);
                        default -> BigDecimal.valueOf(amount);
                    };
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TimeseriesPoint> buildDailyRevenueTimeseries(String tenantId, LocalDateTime start, LocalDateTime end) {
        return invoiceRepository.findDailyRevenueByPaidAt(tenantId, start, end).stream()
                .map(row -> {
                    Object dateObj = row[0];
                    LocalDate date = (dateObj instanceof LocalDate)
                            ? (LocalDate) dateObj
                            : dateObj instanceof java.sql.Date
                            ? ((java.sql.Date) dateObj).toLocalDate()
                            : null;

                    if (date == null) {
                        log.warn("Unexpected null date in revenue timeseries for tenant: {}", tenantId);
                        return null;
                    }

                    Long revenue = ((Number) row[1]).longValue();

                    return new TimeseriesPoint(
                            date.format(DATE_FORMAT),
                            revenue
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SubscriptionMetrics buildSubscriptionMetrics(String tenantId, LocalDateTime start, LocalDateTime end) {
        List<Subscription> activeSubs = subscriptionRepository.findByTenantIdAndStatus(tenantId, "active");
        Integer active = activeSubs.size();
        Integer newSubs = subscriptionRepository
                .countByTenantIdAndCreatedAtAfter(tenantId, start)
                .intValue();
        Integer canceled = subscriptionRepository
                .countCanceledByPeriod(tenantId, start.toLocalDate(), end.toLocalDate())
                .intValue();
        Double churnRate = calculateChurnRate(tenantId, end.toLocalDate()).doubleValue();
        List<PlanDistribution> byPlan = subscriptionRepository
                .countActiveSubscriptionsByPlanCode(tenantId).stream()
                .map(row -> new PlanDistribution(
                        (String) row[0],
                        ((Number) row[1]).intValue()
                ))
                .collect(Collectors.toList());

        return new SubscriptionMetrics(active, newSubs, canceled, churnRate, byPlan);
    }

    private BigDecimal calculateChurnRate(String tenantId, LocalDate now) {
        LocalDate oneMonthAgo = now.minusMonths(1);
        Long activeLastMonth = subscriptionRepository.countActiveAtDate(tenantId, oneMonthAgo);
        Long churnedThisMonth = subscriptionRepository.countChurnedInPeriod(tenantId, oneMonthAgo, now);

        if (activeLastMonth == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((churnedThisMonth * 100.0) / activeLastMonth)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CustomerMetrics buildCustomerMetrics(String tenantId, LocalDateTime start, LocalDateTime end) {
        Long total = customerRepository.countByTenantId(tenantId);
        Long newCustomers = customerRepository.countNewCustomersByPeriod(tenantId, start, end);
        Long activePayers = invoiceRepository.countActivePayersByPeriod(tenantId, start, end);

        Double retentionRate = total > 0
                ? (activePayers.doubleValue() / total.doubleValue()) * 100
                : 0.0;

        return new CustomerMetrics(
                total.intValue(),
                newCustomers.intValue(),
                activePayers.intValue(),
                BigDecimal.valueOf(retentionRate).setScale(1, RoundingMode.HALF_UP).doubleValue()
        );
    }

    private PaymentMetrics buildPaymentMetrics(String tenantId, LocalDateTime start, LocalDateTime end) {
        Map<String, Long> byStatus = invoiceRepository
                .countInvoicesByStatusAndPeriod(tenantId, start, end).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        Long successful = byStatus.getOrDefault("paid", 0L);
        Long failed = byStatus.getOrDefault("failed", 0L);
        Long refunded = byStatus.getOrDefault("refunded", 0L);

        Double conversionRate = (successful + failed) > 0
                ? (successful.doubleValue() / (successful + failed)) * 100
                : 100.0;

        List<FailureReason> failureReasons = invoiceRepository
                .countFailureReasonsByPeriod(tenantId, start, end).stream()
                .map(row -> new FailureReason(
                        (String) row[0],
                        ((Number) row[1]).intValue()
                ))
                .collect(Collectors.toList());

        return new PaymentMetrics(
                successful.intValue(),
                failed.intValue(),
                refunded.intValue(),
                BigDecimal.valueOf(conversionRate).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                failureReasons
        );
    }

    private Double calculateGrowthRate(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : 100.0;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Long countInvoicesByStatus(String tenantId, LocalDateTime start, LocalDateTime end, String status) {
        return invoiceRepository.countInvoicesByStatusAndPeriod(tenantId, start, end).stream()
                .filter(row -> status.equals(row[0]))
                .map(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(0L);
    }
}
