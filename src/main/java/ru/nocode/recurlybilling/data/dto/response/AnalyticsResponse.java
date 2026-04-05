package ru.nocode.recurlybilling.data.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Сводная бизнес-аналитика организации")
public class AnalyticsResponse {

    @Schema(description = "Информация о периоде отчётности")
    private PeriodInfo period;

    @Schema(description = "Метрики выручки")
    private RevenueMetrics revenue;

    @Schema(description = "Метрики подписок")
    private SubscriptionMetrics subscriptions;

    @Schema(description = "Метрики клиентов")
    private CustomerMetrics customers;

    @Schema(description = "Метрики платежей")
    private PaymentMetrics payments;

    @Schema(description = "Время генерации отчёта", example = "2026-03-31T22:27:38.9909694")
    private LocalDateTime generatedAt;

    @Schema(description = "Время жизни кэша в секундах", example = "900")
    private Integer cacheTtl;

    // =================================================================
    // Вложенные классы
    // =================================================================

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Период отчётности")
    public static class PeriodInfo {
        @Schema(description = "Начало текущего периода", example = "2026-03-01")
        public String start;
        @Schema(description = "Конец текущего периода", example = "2026-03-31")
        public String end;
        @Schema(description = "Начало сравнительного периода", example = "2026-02-01")
        public String previousStart;
        @Schema(description = "Конец сравнительного периода", example = "2026-02-29")
        public String previousEnd;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Метрики выручки (в минорной валюте)")
    public static class RevenueMetrics {
        @Schema(description = "MRR — Monthly Recurring Revenue", example = "447000")
        private Long mrr;
        @Schema(description = "ARR — Annual Recurring Revenue", example = "5364000")
        private Long arr;
        @Schema(description = "Общая выручка за период", example = "1341000")
        private Long totalRevenue;
        @Schema(description = "Средний чек", example = "149000")
        private Long averageCheck;
        @Schema(description = "Рост относительно прошлого периода (%)", example = "12.5")
        private Double growth;
        @Schema(description = "Код валюты", example = "RUB")
        private String currency;
        @Schema(description = "Временной ряд выручки по дням")
        private List<TimeseriesPoint> timeseries;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Метрики подписок")
    public static class SubscriptionMetrics {
        @Schema(description = "Активные подписки на конец периода", example = "42")
        private Integer active;
        @Schema(description = "Новые подписки за период", example = "8")
        private Integer newSubscriptions;
        @Schema(description = "Отменённые подписки за период", example = "2")
        private Integer canceled;
        @Schema(description = "Коэффициент оттока (%)", example = "4.8")
        private Double churnRate;
        @Schema(description = "Распределение по тарифным планам")
        private List<PlanDistribution> byPlan;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Метрики клиентов")
    public static class CustomerMetrics {
        @Schema(description = "Всего уникальных клиентов", example = "156")
        private Integer total;
        @Schema(description = "Новых клиентов за период", example = "12")
        private Integer newCustomers;
        @Schema(description = "Клиентов с успешными платежами", example = "42")
        private Integer activePayers;
        @Schema(description = "Коэффициент удержания (%)", example = "94.2")
        private Double retentionRate;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Метрики платежей")
    public static class PaymentMetrics {
        @Schema(description = "Успешных платежей", example = "45")
        private Integer successful;
        @Schema(description = "Неудачных попыток", example = "3")
        private Integer failed;
        @Schema(description = "Возвратов средств", example = "1")
        private Integer refunded;
        @Schema(description = "Конверсия успешных платежей (%)", example = "93.8")
        private Double conversionRate;
        @Schema(description = "Причины неудачных платежей")
        private List<FailureReason> failureReasons;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Точка временного ряда")
    public static class TimeseriesPoint {
        @Schema(description = "Дата", example = "2026-03-01")
        private String date;
        @Schema(description = "Значение метрики", example = "44700")
        private Long value;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Распределение по плану")
    public static class PlanDistribution {
        @Schema(description = "Код тарифного плана", example = "premium_monthly")
        private String planCode;
        @Schema(description = "Количество подписок", example = "14")
        private Integer count;
    }

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    @Schema(description = "Причина неудачного платежа")
    public static class FailureReason {
        @Schema(description = "Код причины", example = "insufficient_funds")
        private String reason;
        @Schema(description = "Количество случаев", example = "2")
        private Integer count;
    }
}