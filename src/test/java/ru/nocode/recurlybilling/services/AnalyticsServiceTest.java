package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nocode.recurlybilling.components.metrics.BusinessMetrics;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PlanRepository planRepository;
    @Mock private BusinessMetrics businessMetrics;

    @InjectMocks
    private AnalyticsService analyticsService;

    private final String tenantId = "test_tenant";

    @Test
    @DisplayName("getAnalytics() должен вернуть корректные метрики при наличии данных")
    void getAnalytics_whenDataExists_shouldReturnCompleteMetrics() {
        setupCommonMocks(10L, 5L, 50L);
        setupSubscriptionMocks(2, 1, 0, List.of());
        setupInvoiceMocks(30000L, List.of(new Object[][]{new Object[]{"paid", 2L}}),
                List.of(new Object[][]{new Object[]{LocalDate.now(), 15000L}}),
                List.of());
        setupChurnMocks(10L, 1L);
        setupPreviousMrrMocks(List.of(createSub(10000L, "month", "active")));

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);

        assertNotNull(response);
        assertNotNull(response.getPeriod());
        assertNotNull(response.getRevenue());
        assertNotNull(response.getSubscriptions());
        assertNotNull(response.getCustomers());
        assertNotNull(response.getPayments());

        assertEquals(15000L, response.getRevenue().getAverageCheck());
        assertEquals(100.0, response.getPayments().getConversionRate());
    }

    @Test
    @DisplayName("getAnalytics() при отсутствии данных должен вернуть нули и безопасные значения")
    void getAnalytics_whenNoData_shouldReturnSafeDefaults() {
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active")).thenReturn(List.of());
        when(invoiceRepository.findPaidRevenueByPaidAtPeriod(eq(tenantId), any(), any())).thenReturn(Optional.of(0L));
        when(invoiceRepository.countInvoicesByStatusAndPeriod(eq(tenantId), any(), any())).thenReturn(List.of());
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(List.of());
        when(subscriptionRepository.countByTenantIdAndCreatedAtAfter(eq(tenantId), any())).thenReturn(0L);
        when(subscriptionRepository.countCanceledByPeriod(eq(tenantId), any(), any())).thenReturn(0L);
        when(subscriptionRepository.countActiveAtDate(eq(tenantId), any())).thenReturn(0L);
        when(subscriptionRepository.countChurnedInPeriod(eq(tenantId), any(), any())).thenReturn(0L);
        when(subscriptionRepository.countActiveSubscriptionsByPlanCode(tenantId)).thenReturn(List.of());
        when(customerRepository.countByTenantId(tenantId)).thenReturn(0L);
        when(customerRepository.countNewCustomersByPeriod(eq(tenantId), any(), any())).thenReturn(0L);
        when(invoiceRepository.countActivePayersByPeriod(eq(tenantId), any(), any())).thenReturn(0L);
        when(invoiceRepository.countFailureReasonsByPeriod(eq(tenantId), any(), any())).thenReturn(List.of());

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);

        assertEquals(0L, response.getRevenue().getMrr());
        assertEquals(0L, response.getRevenue().getArr());
        assertEquals(0L, response.getRevenue().getAverageCheck());
        assertEquals(0.0, response.getRevenue().getGrowth());
        assertEquals(0, response.getSubscriptions().getActive());
        assertEquals(0.0, response.getSubscriptions().getChurnRate());
        assertEquals(0, response.getCustomers().getTotal());
        assertEquals(0.0, response.getCustomers().getRetentionRate());
        assertEquals(100.0, response.getPayments().getConversionRate());
    }

    @Test
    @DisplayName("MRR должен корректно считаться для month, year и semester")
    void getAnalytics_shouldCalculateMrrForDifferentIntervals() {
        Subscription monthly = createSub(12000L, "month", "active");
        Subscription yearly = createSub(120000L, "year", "active");
        Subscription semester = createSub(60000L, "semester", "active");

        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active"))
                .thenReturn(List.of(monthly, yearly, semester));
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(List.of());
        setupInvoiceMocks(0L, List.of(), List.of(), List.of());
        setupChurnMocks(0L, 0L);
        when(customerRepository.countByTenantId(tenantId)).thenReturn(0L);

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);

        assertEquals(32000L, response.getRevenue().getMrr());
    }

    @Test
    @DisplayName("Growth rate должен быть 0.0 при нулевых MRR")
    void getAnalytics_growthRate_whenBothZero_shouldReturn0() {
        setupCommonMocks(0L, 0L, 0L);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active")).thenReturn(List.of());
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(List.of());
        setupInvoiceMocks(0L, List.of(), List.of(), List.of());
        setupChurnMocks(0L, 0L);

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);
        assertEquals(0.0, response.getRevenue().getGrowth());
    }

    @Test
    @DisplayName("Growth rate должен быть 100.0 при росте с 0")
    void getAnalytics_growthRate_whenPreviousZero_shouldReturn100() {
        Subscription current = createSub(10000L, "month", "active");
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active")).thenReturn(List.of(current));
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(List.of());
        setupCommonMocks(0L, 0L, 0L);
        setupInvoiceMocks(10000L, List.of(), List.of(), List.of());
        setupChurnMocks(0L, 0L);

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);
        assertEquals(100.0, response.getRevenue().getGrowth());
    }

    @Test
    @DisplayName("Churn rate должен возвращать 0 при отсутствии активных подписок в прошлом месяце")
    void getAnalytics_churnRate_whenActiveLastMonthZero_shouldReturn0() {
        setupCommonMocks(0L, 0L, 0L);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active")).thenReturn(List.of());
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(List.of());
        setupInvoiceMocks(0L, List.of(), List.of(), List.of());
        setupChurnMocks(0L, 5L);

        AnalyticsResponse response = analyticsService.getAnalytics(tenantId);
        assertEquals(0.0, response.getSubscriptions().getChurnRate());
    }

    private void setupCommonMocks(Long totalCustomers, Long newCustomers, Long activePayers) {
        when(customerRepository.countByTenantId(tenantId)).thenReturn(totalCustomers);
        when(customerRepository.countNewCustomersByPeriod(eq(tenantId), any(), any())).thenReturn(newCustomers);
        when(invoiceRepository.countActivePayersByPeriod(eq(tenantId), any(), any())).thenReturn(activePayers);
    }

    private void setupSubscriptionMocks(int activeCount, int newCount, int canceledCount, List<Object[]> planDist) {
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, "active"))
                .thenReturn(List.of(createSub(15000L, "month", "active")));
        when(subscriptionRepository.countByTenantIdAndCreatedAtAfter(eq(tenantId), any())).thenReturn((long) newCount);
        when(subscriptionRepository.countCanceledByPeriod(eq(tenantId), any(), any())).thenReturn((long) canceledCount);
        when(subscriptionRepository.countActiveSubscriptionsByPlanCode(tenantId)).thenReturn(planDist);
    }

    private void setupInvoiceMocks(Long revenue, List<Object[]> statusCounts,
                                   List<Object[]> timeseries, List<Object[]> failureReasons) {
        when(invoiceRepository.findPaidRevenueByPaidAtPeriod(eq(tenantId), any(), any()))
                .thenReturn(Optional.of(revenue));
        when(invoiceRepository.countInvoicesByStatusAndPeriod(eq(tenantId), any(), any()))
                .thenReturn(statusCounts);
        when(invoiceRepository.findDailyRevenueByPaidAt(eq(tenantId), any(), any()))
                .thenReturn(timeseries);
        when(invoiceRepository.countFailureReasonsByPeriod(eq(tenantId), any(), any()))
                .thenReturn(failureReasons);
    }

    private void setupChurnMocks(Long activeLastMonth, Long churnedThisMonth) {
        when(subscriptionRepository.countActiveAtDate(eq(tenantId), any())).thenReturn(activeLastMonth);
        when(subscriptionRepository.countChurnedInPeriod(eq(tenantId), any(), any())).thenReturn(churnedThisMonth);
    }

    private void setupPreviousMrrMocks(List<Subscription> prevSubs) {
        when(subscriptionRepository.findActiveAtDate(eq(tenantId), any())).thenReturn(prevSubs);
    }

    private Subscription createSub(Long amountCents, String interval, String status) {
        Subscription sub = new Subscription();
        sub.setAmountCents(amountCents);
        sub.setInterval(interval);
        sub.setStatus(status);
        return sub;
    }
}