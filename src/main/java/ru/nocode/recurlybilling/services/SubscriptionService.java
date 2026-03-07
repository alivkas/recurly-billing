package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubscriptionResponse createSubscription(String tenantId, SubscriptionCreateRequest request) {
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
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(tenantId);
        subscription.setCustomerId(customer.getId());
        subscription.setPlanId(plan.getId());
        subscription.setStatus("active");
        subscription.setCurrentPeriodStart(request.startDate() != null ? request.startDate() : LocalDate.now());

        calculateSubscriptionDates(subscription, plan, request.startDate());

        if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
            subscription.setTrialEnd(subscription.getCurrentPeriodStart().plusDays(plan.getTrialDays()));
            subscription.setStatus("trialing");
        }

        subscription.setIsActive(true);
        subscription.setCreatedAt(LocalDateTime.now());

        if (plan.getMetadata() != null) {
            subscription.setMetadata(plan.getMetadata());
        }

        Subscription saved = subscriptionRepository.save(subscription);

        if (!"trialing".equals(saved.getStatus())) {
            paymentService.createPaymentForSubscription(saved);
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
                .toList();
    }

    @Transactional
    public void handlePaymentSuccess(UUID subscriptionId, String paymentId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        subscription.setCurrentPeriodStart(subscription.getCurrentPeriodEnd().plusDays(1));
        subscription.setCurrentPeriodEnd(calculateNextPeriodEnd(subscription.getCurrentPeriodStart(), plan));

        if (subscription.getNextBillingDate() != null) {
            subscription.setNextBillingDate(subscription.getCurrentPeriodEnd().plusDays(1));
        }

        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void handlePaymentFailed(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        subscription.setStatus("past_due");
        subscriptionRepository.save(subscription);

        // TODO: отправить уведомление, попробовать повторить платеж
    }

    // TODO Scheduled
    @Transactional
    public void processScheduledBilling(String tenantId) {
        LocalDate today = LocalDate.now();

        List<Subscription> dueSubscriptions = subscriptionRepository
                .findByTenantIdAndStatusAndNextBillingDateBefore(tenantId, "active", today.plusDays(1));

        for (Subscription subscription : dueSubscriptions) {
            paymentService.createPaymentForSubscription(subscription);
        }
    }

    private void calculateSubscriptionDates(Subscription subscription, Plan plan, LocalDate startDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        subscription.setCurrentPeriodStart(start);

        LocalDate periodEnd = calculateNextPeriodEnd(start, plan);
        subscription.setCurrentPeriodEnd(periodEnd);

        if ("semester".equals(plan.getInterval()) || "custom".equals(plan.getInterval())) {
            subscription.setNextBillingDate(null);
        } else {
            subscription.setNextBillingDate(periodEnd.plusDays(1));
        }
    }

    private LocalDate calculateNextPeriodEnd(LocalDate start, Plan plan) {
        return switch (plan.getInterval()) {
            case "month" -> start.plusMonths(plan.getIntervalCount());
            case "year" -> start.plusYears(plan.getIntervalCount());
            case "semester" -> {
                if (start.getMonthValue() >= 9) {
                    yield LocalDate.of(start.getYear(), 12, 31);
                }
                else if (start.getMonthValue() >= 2) {
                    yield LocalDate.of(start.getYear(), 5, 31);
                }
                else {
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
