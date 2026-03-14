package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
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
        subscription.setStatus("active");
        subscription.setCurrentPeriodStart(request.startDate() != null ? request.startDate() : LocalDate.now());
        subscription.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "bank_card");

        calculateSubscriptionDates(subscription, plan, request.startDate());

        if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
            subscription.setTrialEnd(subscription.getCurrentPeriodStart().plusDays(plan.getTrialDays()));
            subscription.setStatus("trialing");
        }

        subscription.setInterval(plan.getInterval());
        subscription.setAmountCents(plan.getPriceCents());
        subscription.setIsActive(true);
        subscription.setCreatedAt(LocalDateTime.now());

        if (plan.getMetadata() != null) {
            subscription.setMetadata(plan.getMetadata());
        }

        Subscription saved = subscriptionRepository.save(subscription);

        if (!"trialing".equals(saved.getStatus())) {
            paymentService.createPaymentForSubscription(saved, idempotencyKey);
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

        Subscription saved = subscriptionRepository.save(subscription);
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

        subscription.setStatus("past_due");
        subscriptionRepository.save(subscription);

        try {
            List<Invoice> failedInvoices = invoiceRepository.findBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                    subscriptionId, "failed"
            );
            if (!failedInvoices.isEmpty()) {
                log.warn("Retry logic not implemented yet for subscription {}", subscriptionId);
            }
        } catch (Exception e) {
            log.warn("Failed to handle payment failure for subscription {}: {}", subscriptionId, e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Moscow")
    @Transactional
    public void processScheduledBilling() {
        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();

        for (String tenantId : tenantIds) {
            try {
                processBillingForTenant(tenantId);
            } catch (Exception e) {
                log.error("Error processing billing for tenant {}", tenantId, e);
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
            LocalDate end = plan.getEndDate() != null ? plan.getEndDate() : start.plusMonths(1);
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

    private LocalDate calculateNextPeriodEnd(LocalDate start, Plan plan) {
        return switch (plan.getInterval()) {
            case "month" -> start.plusMonths(plan.getIntervalCount());
            case "year" -> start.plusYears(plan.getIntervalCount());
            case "semester" -> {
                if (start.getMonthValue() >= 9) {
                    yield LocalDate.of(start.getYear(), 12, 31);
                } else if (start.getMonthValue() >= 2) {
                    yield LocalDate.of(start.getYear(), 5, 31);
                } else {
                    yield LocalDate.of(start.getYear() + 1, 12, 31);
                }
            }
            case "custom" -> plan.getEndDate() != null ? plan.getEndDate() : start.plusMonths(1);
            default -> throw new IllegalArgumentException("Unsupported interval: " + plan.getInterval());
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