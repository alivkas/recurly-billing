package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.components.metrics.BusinessMetrics;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final PaymentService paymentService;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final TenantService tenantService;
    private final AccessService accessService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final BusinessMetrics businessMetrics;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SubscriptionResponse createSubscription(String tenantId, SubscriptionCreateRequest request, String idempotencyKey) throws JsonProcessingException {
        Customer customer = customerRepository.findByTenantIdAndExternalId(tenantId, request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer with externalId '" + request.customerId() + "' not found"));

        Plan plan = planRepository.findByIdAndTenantId(UUID.fromString(request.planId()), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plan with id '" + request.planId() + "' not found"));

        boolean exists = subscriptionRepository.findByTenantIdAndCustomerExternalId(tenantId, request.customerId()).stream()
                .anyMatch(s -> s.getPlanId().equals(plan.getId()) && "active".equals(s.getStatus()));
        if (exists) {
            throw new IllegalArgumentException("Active subscription for this plan already exists");
        }

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setCustomerId(customer.getId());
        subscription.setPlanId(plan.getId());
        subscription.setCurrentPeriodStart(request.startDate() != null ? request.startDate() : LocalDate.now());
        subscription.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "bank_card");

        if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
            subscription.setTrialEnd(subscription.getCurrentPeriodStart().plusDays(plan.getTrialDays()));
            subscription.setCurrentPeriodEnd(subscription.getTrialEnd());
            subscription.setNextBillingDate(null);
            subscription.setStatus("trialing");

            if (Boolean.TRUE.equals(request.withAutoRenewal())) {
                try {
                    String bindingIdempotencyKey = "bind_" + idempotencyKey;
                    paymentService.bindCardForTrial(subscription, bindingIdempotencyKey);
                } catch (Exception e) {
                    log.warn("Card binding failed for trial subscription {}, continuing without auto-renewal",
                            subscription.getId(), e);
                }
            }
        } else {
            subscription.setCurrentPeriodEnd(null);
            subscription.setNextBillingDate(null);
            subscription.setStatus("pending_payment");
        }

        subscription.setInterval(plan.getInterval());
        subscription.setAmountCents(plan.getPriceCents());
        subscription.setIsActive(true);
        subscription.setCreatedAt(LocalDateTime.now());

        if (plan.getMetadata() != null) {
            subscription.setMetadata(plan.getMetadata());
        }

        Subscription saved = subscriptionRepository.save(subscription);

        businessMetrics.recordSubscriptionCreated(
                tenantId,
                plan.getCode(),
                plan.getTrialDays() != null && plan.getTrialDays() > 0
        );

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("customer_id", request.customerId());
        newValues.put("plan_id", request.planId());
        newValues.put("status", saved.getStatus());
        newValues.put("trial_days", plan.getTrialDays());
        auditLogService.logCreate(tenantId, request.customerId(), "subscription", saved.getId().toString(),
                newValues, "127.0.0.1", "API");

        try {
            if (!"trialing".equals(saved.getStatus())) {
                paymentService.createPaymentForSubscription(saved, idempotencyKey);
            }
        } catch (Exception e) {
            log.error("Failed to create initial payment, but subscription saved", e);
        }

        return convertToResponse(saved);
    }

    @Transactional
    public SubscriptionResponse cancelSubscription(String tenantId, String subscriptionId, SubscriptionCancelRequest request) {
        UUID subId = UUID.fromString(subscriptionId);
        Subscription subscription = subscriptionRepository.findByIdAndTenantId(subId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (!"active".equals(subscription.getStatus()) && !"trialing".equals(subscription.getStatus())) {
            throw new IllegalStateException("Cannot cancel subscription with status: " + subscription.getStatus());
        }

        if (request.cancelImmediately()) {
            subscription.setStatus("cancelled");
            subscription.setCancelAt(LocalDate.now());
        } else {
            subscription.setStatus("cancelled");
            subscription.setCancelAt(subscription.getCurrentPeriodEnd());
        }

        subscription.setCanceledAt(LocalDateTime.now());
        Subscription saved = subscriptionRepository.save(subscription);

        businessMetrics.recordSubscriptionCancelled(
                tenantId,
                request.cancelImmediately() ? "immediate" : "at_period_end",
                request.cancelImmediately()
        );

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("status", "active");
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("status", "cancelled");
        newValues.put("cancel_at", subscription.getCancelAt());
        newValues.put("cancel_immediately", request.cancelImmediately());

        auditLogService.logUpdate(tenantId, "system", "subscription", subscriptionId,
                oldValues, newValues, "127.0.0.1", "API");

        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(String tenantId, String subscriptionId) {
        UUID subId = UUID.fromString(subscriptionId);
        Subscription subscription = subscriptionRepository.findByIdAndTenantId(subId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        return convertToResponse(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptionsByCustomer(String tenantId, String externalId) {
        List<Subscription> subscriptions = subscriptionRepository.findByTenantIdAndCustomerExternalId(tenantId, externalId);
        return subscriptions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void handlePaymentFailed(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow();

        List<Invoice> invoices = invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
        if (invoices.isEmpty()) return;
        Invoice lastInvoice = invoices.get(0);

        int failedAttempts = subscription.getFailedPaymentAttempts() + 1;
        subscription.setFailedPaymentAttempts(failedAttempts);
        subscription.setStatus("past_due");
        subscription.setPastDueSince(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        notificationService.sendPaymentFailedNotification(subscription, lastInvoice, plan);

        if (failedAttempts >= 3) {
            try {
                Customer customer = customerRepository.findById(subscription.getCustomerId())
                        .orElseThrow();
                accessService.revokeAccessOnPaymentFailure(UUID.fromString(customer.getExternalId()), plan.getCode());
            } catch (Exception e) {
                log.error("Failed to revoke access for subscription {}", subscriptionId, e);
            }
        }

        log.warn("Payment failed for subscription {}. Attempts: {}", subscriptionId, failedAttempts);
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Moscow")
    @Transactional
    public void processScheduledBilling() {
        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();

        for (String tenantId : tenantIds) {
            try {
                processExpiredTrials(tenantId);
                processBillingForTenant(tenantId);
            } catch (Exception e) {
                log.error("Error processing billing for tenant {}", tenantId, e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    @Transactional
    public void sendTrialEndingNotifications() {
        LocalDate threeDaysFromNow = LocalDate.now().plusDays(1);

        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();
        for (String tenantId : tenantIds) {
            try {
                List<Subscription> endingTrials = subscriptionRepository
                        .findByTenantIdAndStatusAndTrialEndBefore(tenantId, "trialing", threeDaysFromNow.plusDays(1))
                        .stream()
                        .filter(s -> s.getTrialEnd() != null && !s.getTrialEnd().isBefore(threeDaysFromNow))
                        .collect(Collectors.toList());

                for (Subscription subscription : endingTrials) {
                    Plan plan = planRepository.findById(subscription.getPlanId()).orElseThrow();
                    notificationService.sendTrialEndingNotification(subscription, plan);
                    log.info("Trial ending notification sent for subscription {}", subscription.getId());
                }
            } catch (Exception e) {
                log.error("Error sending trial ending notifications for tenant {}", tenantId, e);
            }
        }
    }

    @Transactional
    public void processBillingForTenant(String tenantId) {
        LocalDate today = LocalDate.now();
        List<Subscription> dueSubscriptions = subscriptionRepository
                .findByTenantIdAndStatusAndNextBillingDateBefore(tenantId, "active", today.plusDays(1));

        for (Subscription subscription : dueSubscriptions) {
            if ("cancelled".equals(subscription.getStatus())) {
                log.info("Skipping cancelled subscription: {}", subscription.getId());
                continue;
            }

            try {
                String idempotencyKey = "sched_" + UUID.randomUUID().toString().replace("-", "");
                paymentService.createPaymentForSubscription(subscription, idempotencyKey);
                log.info("Scheduled payment created for subscription {}", subscription.getId());
            } catch (Exception e) {
                log.error("Failed to create scheduled payment for subscription {}", subscription.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Moscow")
    @Transactional
    public void processFailedPayments() {
        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();

        for (String tenantId : tenantIds) {
            try {
                processFailedPaymentsForTenant(tenantId);
            } catch (Exception e) {
                log.error("Error processing failed payments for tenant {}", tenantId, e);
            }
        }
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Europe/Moscow")
    @Transactional
    public void revokeAccessForCancelledSubscriptions() {
        LocalDate today = LocalDate.now();

        List<Subscription> toRevoke = subscriptionRepository
                .findByStatusAndCancelAtBefore("cancelled", today.plusDays(1));

        for (Subscription sub : toRevoke) {
            try {
                Customer customer = customerRepository.findById(sub.getCustomerId()).orElseThrow();
                Plan plan = planRepository.findById(sub.getPlanId()).orElseThrow();

                accessService.revokeAccessOnPaymentFailure(
                        UUID.fromString(customer.getExternalId()),
                        plan.getCode()
                );

                log.info("Access revoked for cancelled subscription: {}", sub.getId());
            } catch (Exception e) {
                log.error("Failed to revoke access for subscription {}", sub.getId(), e);
            }
        }
    }

    private void processFailedPaymentsForTenant(String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<Invoice> retryableInvoices = invoiceRepository
                .findByTenantIdAndStatusAndNextRetryAtBefore(tenantId, "failed", now);

        for (Invoice invoice : retryableInvoices) {
            try {
                Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                        .orElseThrow(() -> new IllegalStateException("Subscription not found"));

                if (!"active".equals(subscription.getStatus())) {
                    log.info("Skipping retry for non-active subscription: {}", subscription.getId());
                    continue;
                }

                String idempotencyKey = "retry_" + invoice.getId() + "_" + System.currentTimeMillis();
                paymentService.createPaymentForSubscription(subscription, idempotencyKey);
                log.info("Retry payment created for invoice: {}", invoice.getId());
            } catch (Exception e) {
                log.error("Failed to retry payment for invoice: {}", invoice.getId(), e);
            }
        }
    }

    public void validateTenantAndApiKey(String tenantId, String apiKey) {
        tenantService.validateTenantAndApiKey(tenantId, apiKey);
    }

    private void calculateSubscriptionDates(Subscription subscription, Plan plan, LocalDate start) {
        if ("semester".equals(plan.getInterval()) || "custom".equals(plan.getInterval())) {
            LocalDate end = plan.getEndDate() != null ? plan.getEndDate() : start.plusMonths(1).minusDays(1);
            subscription.setCurrentPeriodEnd(end);
            subscription.setNextBillingDate(null);
        } else {
            LocalDate firstPeriodEnd = calculateNextPeriodEnd(start, plan);

            if (plan.getEndDate() != null && firstPeriodEnd.isAfter(plan.getEndDate())) {
                firstPeriodEnd = plan.getEndDate();
            }

            subscription.setCurrentPeriodEnd(firstPeriodEnd);
            if (plan.getEndDate() == null || firstPeriodEnd.isBefore(plan.getEndDate())) {
                subscription.setNextBillingDate(firstPeriodEnd.plusDays(1));
            } else {
                subscription.setNextBillingDate(null);
            }
        }
    }

    private void processExpiredTrials(String tenantId) {
        LocalDate today = LocalDate.now();
        List<Subscription> expiredTrials = subscriptionRepository
                .findByTenantIdAndStatusAndTrialEndBefore(tenantId, "trialing", today.plusDays(1));

        for (Subscription subscription : expiredTrials) {
            try {
                Plan plan = planRepository.findById(subscription.getPlanId())
                        .orElseThrow(() -> new IllegalStateException("Plan not found"));

                LocalDate periodStart = subscription.getTrialEnd().plusDays(1);
                LocalDate periodEnd = calculateNextPeriodEnd(periodStart, plan);

                if (plan.getEndDate() != null && periodEnd.isAfter(plan.getEndDate())) {
                    periodEnd = plan.getEndDate();
                }

                subscription.setCurrentPeriodStart(periodStart);
                subscription.setCurrentPeriodEnd(periodEnd);
                subscription.setStatus("active");

                if (plan.getEndDate() == null || periodEnd.isBefore(plan.getEndDate())) {
                    subscription.setNextBillingDate(periodEnd.plusDays(1));
                } else {
                    subscription.setNextBillingDate(null);
                }

                subscriptionRepository.save(subscription);

                String idempotencyKey = "trial_end_" + subscription.getId() + "_" + System.currentTimeMillis();
                paymentService.createPaymentForSubscription(subscription, idempotencyKey);

                businessMetrics.recordTrialExpired(subscription.getTenantId(), plan.getCode());

                log.info("Trial ended for subscription {}. First payment created.", subscription.getId());
            } catch (Exception e) {
                log.error("Failed to process expired trial for subscription: {}", subscription.getId(), e);
            }
        }
    }

    private LocalDate calculateNextPeriodEnd(LocalDate start, Plan plan) {
        return switch (plan.getInterval()) {
            case "month" ->
                    start.plusMonths(plan.getIntervalCount()).minusDays(1);
            case "year" ->
                    start.plusYears(plan.getIntervalCount()).minusDays(1);
            case "semester" -> {
                if (start.getMonthValue() >= 9) {
                    yield LocalDate.of(start.getYear(), 12, 31);
                } else if (start.getMonthValue() >= 2) {
                    yield LocalDate.of(start.getYear(), 5, 31);
                } else {
                    yield LocalDate.of(start.getYear() + 1, 12, 31);
                }
            }
            case "custom" ->
                    plan.getEndDate() != null ? plan.getEndDate() : start.plusMonths(1).minusDays(1);
            default ->
                    start.plusMonths(1).minusDays(1);
        };
    }

    private SubscriptionResponse convertToResponse(Subscription subscription) {
        Map<String, Object> metadata = null;
        if (subscription.getMetadata() != null) {
            metadata = objectMapper.convertValue(subscription.getMetadata(), Map.class);
        }

        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getNextBillingDate(),
                metadata,
                subscription.getCreatedAt()
        );
    }
}