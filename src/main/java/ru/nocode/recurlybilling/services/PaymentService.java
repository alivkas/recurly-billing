package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaClient;
import ru.nocode.recurlybilling.data.dto.request.YooKassaPaymentRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.YooKassaPaymentResponse;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
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
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final YooKassaClient yooKassaClient;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final AccessService accessService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse createPaymentForSubscription(Subscription subscription, String idempotencyKey) throws JsonProcessingException {
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found for subscription"));

        Invoice invoice = new Invoice();
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setAmountCents(plan.getPriceCents());
        invoice.setStatus("pending");
        validatePaymentMethod(subscription.getPaymentMethod());
        invoice.setPaymentMethod(subscription.getPaymentMethod());
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        try {
            YooKassaPaymentRequest request = buildYooKassaRequest(savedInvoice, subscription, plan);

            YooKassaPaymentResponse response = yooKassaClient.createPayment(request, idempotencyKey);

            if (response.getPaymentMethod() != null &&
                    response.getPaymentMethod().getId() != null &&
                    subscription.getPaymentMethodId() == null) {

                subscription.setPaymentMethodId(response.getPaymentMethod().getId());
                subscriptionRepository.save(subscription);
                log.info("Saved payment_method_id: {} for subscription: {}",
                        response.getPaymentMethod().getId(), subscription.getId());
            }

            String confirmationUrl = null;
            if (response.getConfirmation() != null &&
                    "redirect".equals(response.getConfirmation().getType())) {
                confirmationUrl = response.getConfirmation().getConfirmationUrl();
            }

            String mappedStatus = mapYooKassaStatus(response.getStatus());
            savedInvoice.setPaymentId(response.getId());
            savedInvoice.setStatus(mappedStatus);
            savedInvoice.setConfirmationUrl(confirmationUrl);
            savedInvoice.setUpdatedAt(LocalDateTime.now());

            invoiceRepository.save(savedInvoice);

            if ("paid".equals(mappedStatus)) {
                if (subscription.getNextBillingDate() != null) {
                    extendSubscriptionPeriod(savedInvoice);
                } else {
                    log.info("First payment for subscription {}, no extension needed", subscription.getId());
                }
            }

            return new PaymentResponse(
                    response.getId(),
                    mappedStatus,
                    confirmationUrl,
                    savedInvoice.getAmountCents(),
                    "RUB",
                    response.getCreatedAt()
            );

        } catch (HttpClientErrorException e) {
            handleYooKassaClientError(e, savedInvoice);
            throw new RuntimeException("Payment rejected by YooKassa: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            savedInvoice.setStatus("failed");
            savedInvoice.setUpdatedAt(LocalDateTime.now());
            handleFailedPayment(savedInvoice, subscription);
            invoiceRepository.save(savedInvoice);
            throw new RuntimeException("YooKassa unavailable", e);
        }
    }

    @Transactional
    public void handleYooKassaWebhook(String paymentId, String status, Map<String, Object> webhook, String tenantId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findByPaymentId(paymentId);

        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice not found for paymentId: {}. Skipping webhook processing.", paymentId);
            return;
        }

        Invoice invoice = invoiceOpt.get();
        if (!invoice.getTenantId().equals(tenantId)) {
            throw new SecurityException("Invoice does not belong to tenant: " + tenantId);
        }

        if (!invoice.getTenantId().equals(tenantId)) {
            throw new SecurityException("Invoice does not belong to tenant: " + tenantId);
        }

        String oldStatus = invoice.getStatus();
        String mappedStatus = mapYooKassaStatus(status);
        invoice.setStatus(mappedStatus);
        invoice.setUpdatedAt(LocalDateTime.now());

        Map<String, Object> payment = (Map<String, Object>) webhook.get("object");
        Map<String, Object> metadata = (Map<String, Object>) payment.get("metadata");

        if (metadata != null && !metadata.isEmpty()) {
            invoice.setMetadata(objectMapper.valueToTree(metadata));
        }

        Map<String, Object> paymentMethod = (Map<String, Object>) payment.get("payment_method");
        if (paymentMethod != null) {
            String pmId = (String) paymentMethod.get("id");
            if (pmId != null) {
                Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                        .orElseThrow(() -> new IllegalStateException("Subscription not found"));
                subscription.setPaymentMethodId(pmId);
                subscriptionRepository.save(subscription);
            }
        }

        if ("paid".equals(mappedStatus) && !"paid".equals(oldStatus)) {
            extendSubscriptionPeriod(invoice);

            Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                    .orElseThrow();
            Plan plan = planRepository.findById(subscription.getPlanId())
                    .orElseThrow();

            accessService.grantAccess(
                    subscription.getTenantId(),
                    String.valueOf(subscription.getCustomerId()),
                    plan.getCode(),
                    subscription.getCurrentPeriodEnd()
            );

            auditLogService.logPaymentSuccess(
                    tenantId,
                    subscription.getCustomerId().toString(),
                    paymentId,
                    invoice.getAmountCents(),
                    "127.0.0.1",
                    "YooKassa Webhook"
            );

            notificationService.sendPaymentSucceededNotification(subscription, invoice, plan);
        }

        invoiceRepository.save(invoice);
        log.info("Webhook processed: paymentId={}, status={}, tenant={}", paymentId, mappedStatus, tenantId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTenant(String paymentId, String tenantId) {
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));

        if (!invoice.getTenantId().equals(tenantId)) {
            throw new IllegalStateException("Payment does not belong to tenant");
        }

        return new PaymentResponse(
                invoice.getPaymentId(),
                invoice.getStatus(),
                invoice.getConfirmationUrl(),
                invoice.getAmountCents(),
                "RUB",
                invoice.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PaymentResponse getLastPaymentForSubscription(UUID subscriptionId) {
        List<Invoice> invoices = invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);

        if (invoices.isEmpty()) {
            return null;
        }

        Invoice latest = invoices.get(0);
        return new PaymentResponse(
                latest.getPaymentId(),
                latest.getStatus(),
                latest.getConfirmationUrl(),
                latest.getAmountCents(),
                "RUB",
                latest.getCreatedAt()
        );
    }

    private void handleFailedPayment(Invoice invoice, Subscription subscription) {
        int maxAttempts = 3;
        int currentAttempt = invoice.getAttemptCount() + 1;
        invoice.setAttemptCount(currentAttempt);

        if (currentAttempt < maxAttempts) {
            LocalDateTime nextRetry = switch (currentAttempt) {
                case 1 -> LocalDateTime.now().plusDays(1);   // 1-я попытка — через 1 день
                case 2 -> LocalDateTime.now().plusDays(3);   // 2-я — через 3 дня
                default -> LocalDateTime.now().plusDays(7);  // 3-я — через 7 дней
            };
            invoice.setNextRetryAt(nextRetry);
            log.info("Scheduling retry #{} for subscription {} at {}", currentAttempt, subscription.getId(), nextRetry);
        } else {
            subscription.setStatus("past_due");
            subscriptionRepository.save(subscription);

            auditLogService.logPaymentFailed(
                    subscription.getTenantId(),
                    subscription.getCustomerId().toString(),
                    invoice.getPaymentId(),
                    invoice.getAmountCents(),
                    invoice.getAttemptCount(),
                    "127.0.0.1",
                    "Scheduled Task"
            );


            log.warn("Max retry attempts reached for subscription {}. Marked as past_due.", subscription.getId());
        }
    }

    private void extendSubscriptionPeriod(Invoice invoice) {
        Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                .orElseThrow(() -> new IllegalStateException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        LocalDate newPeriodStart = subscription.getCurrentPeriodEnd().plusDays(1);
        LocalDate newPeriodEnd = calculateNextPeriodEnd(newPeriodStart, plan);

        if (plan.getEndDate() != null && newPeriodEnd.isAfter(plan.getEndDate())) {
            newPeriodEnd = plan.getEndDate();
        }

        if (plan.getEndDate() != null && newPeriodStart.isAfter(plan.getEndDate())) {
            subscription.setNextBillingDate(null);
            subscriptionRepository.save(subscription);
            return;
        }

        subscription.setCurrentPeriodStart(newPeriodStart);
        subscription.setCurrentPeriodEnd(newPeriodEnd);

        subscription.setFailedPaymentAttempts(0);
        subscription.setPastDueSince(null);

        if ("past_due".equals(subscription.getStatus())) {
            subscription.setStatus("active");
            log.info("Subscription {} restored to active after successful payment", subscription.getId());
        }

        if (plan.getEndDate() == null || newPeriodEnd.isBefore(plan.getEndDate())) {
            subscription.setNextBillingDate(newPeriodEnd.plusDays(1));
        } else {
            subscription.setNextBillingDate(null);
        }

        subscriptionRepository.save(subscription);
        log.info("Subscription {} extended: {} → {}",
                subscription.getId(), newPeriodStart, newPeriodEnd);
    }

    private void validatePaymentMethod(String paymentMethod) {
        Set<String> allowedMethods = Set.of("bank_card", "sbp", "mir", "apple_pay", "google_pay");
        if (paymentMethod != null && !allowedMethods.contains(paymentMethod)) {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod + ". Allowed: " + allowedMethods);
        }
    }

    private void handleYooKassaClientError(HttpClientErrorException e, Invoice invoice) {
        invoice.setStatus("failed");
        invoice.setUpdatedAt(LocalDateTime.now());

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("yookassa_error_code", e.getStatusCode().value());
            metadata.put("yookassa_error_message", e.getResponseBodyAsString());
            invoice.setMetadata(objectMapper.valueToTree(metadata));
            invoiceRepository.save(invoice);
        } catch (Exception ex) {
            log.warn("Failed to save error metadata", ex);
        }
    }

    private YooKassaPaymentRequest buildYooKassaRequest(Invoice invoice, Subscription subscription, Plan plan) {
        YooKassaPaymentRequest request = new YooKassaPaymentRequest();

        BigDecimal amountRub = new BigDecimal(invoice.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        request.setAmount(new YooKassaPaymentRequest.Amount(amountRub.longValue(), "RUB"));
        request.setDescription("Оплата подписки #" + invoice.getId());

        String description;
        if (subscription.getPaymentMethodId() != null) {
            request.setPaymentMethodId(subscription.getPaymentMethodId());
            description = "Автоматическое продление подписки #" + subscription.getId();
        } else {
            request.setPaymentMethodData(new YooKassaPaymentRequest.PaymentMethodData("bank_card"));
            request.setSavePaymentMethod(true);

            String returnUrl = environment.getProperty("payment.return-url",
                    "https://your-ngrok-url.ngrok.io/success");
            description = "Оплата подписки #" + invoice.getId();
            request.setConfirmation(new YooKassaPaymentRequest.Confirmation(returnUrl.trim()));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId().toString());
        metadata.put("tenantId", subscription.getTenantId());
        metadata.put("invoiceId", invoice.getId().toString());
        metadata.put("planCode", plan.getCode());
        request.setMetadata(metadata);

        request.setCapture(true);
        request.setDescription(description);

        return request;
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
            default -> start.plusMonths(1);
        };
    }

    private String mapYooKassaStatus(String yooStatus) {
        return switch (yooStatus) {
            case "waiting_for_capture" -> "pending";
            case "succeeded" -> "paid";
            case "canceled" -> "cancelled";
            default -> yooStatus;
        };
    }
}
