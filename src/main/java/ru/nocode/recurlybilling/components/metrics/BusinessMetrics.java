package ru.nocode.recurlybilling.components.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    private static final String PREFIX = "recurly";

    public void recordPaymentSuccess(String tenantId, Long amountCents, String currency) {
        meterRegistry.counter(PREFIX + ".payments.success",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("currency", currency)
                )).increment();

        meterRegistry.summary(PREFIX + ".payments.amount_cents",
                        List.of(Tag.of("tenant_id", tenantId), Tag.of("currency", currency)))
                .record(amountCents);

        log.debug("Recorded successful payment: tenant={}, amount={}, currency={}",
                tenantId, amountCents, currency);
    }

    public void recordPaymentFailure(String tenantId, String reason, String currency) {
        meterRegistry.counter(PREFIX + ".payments.failed",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("reason", reason != null ? reason : "unknown"),
                        Tag.of("currency", currency)
                )).increment();
    }

    public void recordSubscriptionCreated(String tenantId, String planCode, boolean hasTrial) {
        meterRegistry.counter(PREFIX + ".subscriptions.created",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("plan_code", planCode),
                        Tag.of("has_trial", String.valueOf(hasTrial))
                )).increment();
    }

    public void recordSubscriptionCancelled(String tenantId, String reason, boolean immediate) {
        meterRegistry.counter(PREFIX + ".subscriptions.cancelled",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("reason", reason != null ? reason : "unknown"),
                        Tag.of("immediate", String.valueOf(immediate))
                )).increment();
    }

    public void recordTrialConverted(String tenantId, String planCode) {
        meterRegistry.counter(PREFIX + ".trials.converted",
                        List.of(Tag.of("tenant_id", tenantId), Tag.of("plan_code", planCode)))
                .increment();
    }

    public void recordTrialExpired(String tenantId, String planCode) {
        meterRegistry.counter(PREFIX + ".trials.expired",
                        List.of(Tag.of("tenant_id", tenantId), Tag.of("plan_code", planCode)))
                .increment();
    }

    public void recordWebhookReceived(String tenantId, String eventType, String status) {
        meterRegistry.counter(PREFIX + ".webhooks.received",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("event_type", eventType),
                        Tag.of("status", status)
                )).increment();
    }

    public void recordWebhookError(String tenantId, String eventType, String error) {
        meterRegistry.counter(PREFIX + ".webhooks.errors",
                List.of(
                        Tag.of("tenant_id", tenantId),
                        Tag.of("event_type", eventType),
                        Tag.of("error", error != null ? error : "unknown")
                )).increment();
    }

    public void recordAuthFailure(String tenantId, String reason) {
        meterRegistry.counter(PREFIX + ".auth.failures",
                        List.of(Tag.of("tenant_id", tenantId), Tag.of("reason", reason)))
                .increment();
    }

    public void updateMrrGauge(String tenantId, Long mrrCents, String currency) {
        meterRegistry.gauge(PREFIX + ".mrr.cents",
                List.of(Tag.of("tenant_id", tenantId), Tag.of("currency", currency)),
                mrrCents);
    }

    public void updateActiveSubscriptionsGauge(String tenantId, Long count) {
        meterRegistry.gauge(PREFIX + ".subscriptions.active.count",
                List.of(Tag.of("tenant_id", tenantId)),
                count);
    }
}
