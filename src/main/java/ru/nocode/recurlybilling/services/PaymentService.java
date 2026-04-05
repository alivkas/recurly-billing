package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
        invoice.setCurrency(plan.getCurrency() != null ? plan.getCurrency() : "RUB");
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
                    subscription = subscriptionRepository.findById(invoice.getSubscriptionId()).orElseThrow();
                } else {
                    log.info("First payment for subscription {}, no extension needed", subscription.getId());
                }
                onPaymentSuccess(savedInvoice, subscription.getTenantId(), savedInvoice.getPaymentId());
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
    public void handleYooKassaWebhook(String paymentId,
                                      String status,
                                      Map<String, Object> webhook,
                                      String tenantId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findByPaymentId(paymentId);
        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice not found for paymentId: {}. Skipping webhook processing.", paymentId);
            return;
        }

        Invoice invoice = invoiceOpt.get();
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
        if ("canceled".equals(mappedStatus) && "authorization".equals(oldStatus)) {
            log.info("Card binding authorization canceled (expected): paymentId={}", paymentId);
            invoiceRepository.save(invoice);
            return;
        }
        if ("paid".equals(mappedStatus) && !"paid".equals(oldStatus)) {
            Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                    .orElseThrow();
            Plan plan = planRepository.findById(subscription.getPlanId())
                    .orElseThrow(() -> new IllegalStateException("Plan not found"));
            invoice.setPaidAt(LocalDateTime.now());

            if (payment.containsKey("currency")) {
                invoice.setCurrency((String) payment.get("currency"));
            }
            if ("pending_deferred".equals(oldStatus)) {
                handleTrialConversionToPaid(subscription, plan, invoice);
            }
            else {
                List<Invoice> allInvoices = invoiceRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId());
                boolean isFirstPayment = allInvoices.size() == 1;

                if (isFirstPayment) {
                    initializeFirstPaymentPeriods(subscription, plan);
                } else {
                    extendSubscriptionPeriod(invoice);
                    subscription = subscriptionRepository.findById(invoice.getSubscriptionId()).orElseThrow();
                }
            }

            onPaymentSuccess(invoice, tenantId, paymentId);
        }

        invoiceRepository.save(invoice);

        if ("failed".equals(mappedStatus) || "cancelled".equals(mappedStatus)) {
            String failureReason = extractFailureReason(webhook, payment);
            if (failureReason != null) {
                invoice.setFailureReason(failureReason);
                invoiceRepository.save(invoice);
                log.debug("Set failure reason for invoice {}: {}", invoice.getId(), failureReason);
            }
        }

        log.info("Webhook processed: paymentId={}, oldStatus={}, newStatus={}, tenant={}",
                paymentId, oldStatus, mappedStatus, tenantId);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse bindCardForTrial(Subscription subscription, String idempotencyKey)
            throws JsonProcessingException {

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        Invoice invoice = new Invoice();
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setAmountCents(100L);
        invoice.setStatus("authorization");
        invoice.setPaymentMethod(subscription.getPaymentMethod());
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setCurrency(plan.getCurrency() != null ? plan.getCurrency() : "RUB");
        invoice.setMetadata(objectMapper.valueToTree(Map.of(
                "purpose", "card_binding_for_trial",
                "subscriptionId", subscription.getId().toString()
        )));
        Invoice savedInvoice = invoiceRepository.save(invoice);

        try {
            YooKassaPaymentRequest request = new YooKassaPaymentRequest();

            request.setAmount(new YooKassaPaymentRequest.Amount(1L, "RUB"));
            request.setDescription("Проверка карты для триала: " + plan.getName());
            request.setCapture(false);
            request.setSavePaymentMethod(true);

            String returnUrl = environment.getProperty("payment.return-url",
                    "https://app.com/trial/activated");
            request.setConfirmation(new YooKassaPaymentRequest.Confirmation(returnUrl));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("subscriptionId", subscription.getId().toString());
            metadata.put("tenantId", subscription.getTenantId());
            metadata.put("purpose", "card_binding_for_trial");
            request.setMetadata(metadata);

            YooKassaPaymentResponse response = yooKassaClient.createPayment(request, idempotencyKey);

            if (response.getPaymentMethod() != null && response.getPaymentMethod().getId() != null) {
                subscription.setPaymentMethodId(response.getPaymentMethod().getId());
                subscription.setCardBoundAt(LocalDateTime.now());
                subscription.setAutoRenewalEnabled(true);
                subscriptionRepository.save(subscription);
                log.info("Card bound for subscription {}: payment_method_id={}",
                        subscription.getId(), response.getPaymentMethod().getId());
            }

            savedInvoice.setPaymentId(response.getId());
            savedInvoice.setStatus(mapYooKassaStatus(response.getStatus()));
            invoiceRepository.save(savedInvoice);

            if ("waiting_for_capture".equals(response.getStatus())) {
                log.info("Canceling authorization payment: {}", response.getId());
                //String cancelIdempotencyKey = idempotencyKey + "_cancel";
                yooKassaClient.cancelPayment(response.getId());

                savedInvoice.setStatus("canceled");
                savedInvoice.setUpdatedAt(LocalDateTime.now());
                invoiceRepository.save(savedInvoice);
            }

            return new PaymentResponse(
                    response.getId(),
                    "authorization_canceled",
                    response.getConfirmation() != null ? response.getConfirmation().getConfirmationUrl() : null,
                    100L,
                    "RUB",
                    response.getCreatedAt()
            );

        } catch (HttpClientErrorException e) {
            log.error("YooKassa error during card binding", e);
            savedInvoice.setStatus("failed");
            invoiceRepository.save(savedInvoice);
            throw new RuntimeException("Card binding failed: " + e.getResponseBodyAsString(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse createDeferredPaymentForTrial(Subscription subscription, String idempotencyKey)
            throws JsonProcessingException {

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        Invoice invoice = new Invoice();
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setAmountCents(plan.getPriceCents());
        invoice.setStatus("pending_deferred");
        invoice.setPaymentMethod(subscription.getPaymentMethod());
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setCurrency(plan.getCurrency() != null ? plan.getCurrency() : "RUB");
        invoice.setMetadata(objectMapper.valueToTree(Map.of(
                "purpose", "trial_ending_payment",
                "source", "notification",
                "subscriptionId", subscription.getId().toString()
        )));
        Invoice savedInvoice = invoiceRepository.save(invoice);

        try {
            YooKassaPaymentRequest request = buildYooKassaRequest(savedInvoice, subscription, plan);

            request.setPaymentMethodData(new YooKassaPaymentRequest.PaymentMethodData("bank_card"));
            request.setSavePaymentMethod(true);

            String returnUrl = environment.getProperty("payment.return-url",
                    "https://app.com/success");
            request.setConfirmation(new YooKassaPaymentRequest.Confirmation(returnUrl.trim()));

            YooKassaPaymentResponse response = yooKassaClient.createPayment(request, idempotencyKey);

            if (response.getPaymentMethod() != null && response.getPaymentMethod().getId() != null) {
                subscription.setPaymentMethodId(response.getPaymentMethod().getId());
                subscription.setCardBoundAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);
            }

            String mappedStatus = mapYooKassaStatus(response.getStatus());

            savedInvoice.setPaymentId(response.getId());
            savedInvoice.setStatus(mappedStatus);

            String confirmationUrl = null;
            if (response.getConfirmation() != null &&
                    "redirect".equals(response.getConfirmation().getType())) {
                confirmationUrl = response.getConfirmation().getConfirmationUrl();
            }
            savedInvoice.setConfirmationUrl(confirmationUrl);
            savedInvoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(savedInvoice);

            return new PaymentResponse(
                    response.getId(),
                    mappedStatus,
                    confirmationUrl,
                    savedInvoice.getAmountCents(),
                    "RUB",
                    response.getCreatedAt()
            );

        } catch (HttpClientErrorException e) {
            log.error("YooKassa error during deferred payment creation", e);
            savedInvoice.setStatus("failed");
            invoiceRepository.save(savedInvoice);
            throw new RuntimeException("Payment creation failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Unexpected error during deferred payment creation", e);
            savedInvoice.setStatus("failed");
            invoiceRepository.save(savedInvoice);
            throw new RuntimeException("Payment creation failed", e);
        }
    }

    private String extractFailureReason(Map<String, Object> webhook, Map<String, Object> payment) {
        if (payment.containsKey("cancellation_details")) {
            Map<String, Object> details = (Map<String, Object>) payment.get("cancellation_details");
            if (details != null && details.containsKey("reason")) {
                return mapYooKassaFailureReason((String) details.get("reason"));
            }
        }

        if (payment.containsKey("metadata")) {
            Map<String, Object> metadata = (Map<String, Object>) payment.get("metadata");
            if (metadata != null && metadata.containsKey("failure_reason")) {
                return (String) metadata.get("failure_reason");
            }
        }

        if ("canceled".equals(payment.get("status"))) {
            return "payment_canceled";
        }

        return null;
    }

    private String mapYooKassaFailureReason(String yooReason) {
        if (yooReason == null) return null;

        return switch (yooReason.toLowerCase()) {
            case "expired", "card_expired" -> "card_expired";
            case "fraud_suspected", "fraud" -> "fraud_detected";
            case "insufficient_funds", "not_enough_money" -> "insufficient_funds";
            case "card_declined", "declined" -> "card_declined";
            case "invalid_cvc", "invalid_cvv" -> "invalid_cvc";
            case "authentication_failed", "3d_secure_failed" -> "authentication_failed";
            case "canceled_by_merchant" -> "canceled_by_merchant";
            default -> "processing_error";
        };
    }

    private void handleTrialConversionToPaid(Subscription subscription, Plan plan, Invoice invoice) {
        log.info("Converting trial subscription to paid: subscription={}, invoice={}",
                subscription.getId(), invoice.getId());

        LocalDate paymentDate = LocalDate.now();
        subscription.setCurrentPeriodStart(paymentDate);

        LocalDate periodEnd = calculateNextPeriodEnd(paymentDate, plan);
        if (plan.getEndDate() != null && periodEnd.isAfter(plan.getEndDate())) {
            periodEnd = plan.getEndDate();
        }
        subscription.setCurrentPeriodEnd(periodEnd);

        if (plan.getEndDate() == null || periodEnd.isBefore(plan.getEndDate())) {
            subscription.setNextBillingDate(periodEnd.plusDays(1));
        } else {
            subscription.setNextBillingDate(null);
        }

        subscription.setStatus("active");
        subscription.setTrialEnd(null);
        subscription.setFailedPaymentAttempts(0);
        subscription.setPastDueSince(null);

        subscriptionRepository.save(subscription);

        log.info("Trial converted: subscription={}, nextBillingDate={}",
                subscription.getId(), subscription.getNextBillingDate());
    }

    private void initializeFirstPaymentPeriods(Subscription subscription, Plan plan) {
        LocalDate paymentDate = LocalDate.now();
        subscription.setCurrentPeriodStart(paymentDate);

        LocalDate periodEnd = calculateNextPeriodEnd(paymentDate, plan);
        if (plan.getEndDate() != null && periodEnd.isAfter(plan.getEndDate())) {
            periodEnd = plan.getEndDate();
        }
        subscription.setCurrentPeriodEnd(periodEnd);

        if (plan.getEndDate() == null || periodEnd.isBefore(plan.getEndDate())) {
            subscription.setNextBillingDate(periodEnd.plusDays(1));
        } else {
            subscription.setNextBillingDate(null);
        }

        subscription.setStatus("active");
        subscriptionRepository.save(subscription);
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

        LocalDate newPeriodEnd = calculateNextPeriodEnd(newPeriodStart, plan).minusDays(1);

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
        }

        if (plan.getEndDate() == null || newPeriodEnd.isBefore(plan.getEndDate())) {
            subscription.setNextBillingDate(newPeriodEnd.plusDays(1));
        } else {
            subscription.setNextBillingDate(null);
        }

        subscriptionRepository.save(subscription);
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

        String failureReason = parseFailureReasonFromError(e);
        if (failureReason != null) {
            invoice.setFailureReason(failureReason);
        }

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

    private String parseFailureReasonFromError(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body == null) return null;

            JsonNode errorNode = objectMapper.readTree(body);
            if (errorNode.has("error") && errorNode.get("error").has("description")) {
                String description = errorNode.get("error").get("description").asText().toLowerCase();

                if (description.contains("недостаточно средств") || description.contains("insufficient")) {
                    return "insufficient_funds";
                } else if (description.contains("срок действия") || description.contains("expired")) {
                    return "card_expired";
                } else if (description.contains("отклонён") || description.contains("declined")) {
                    return "card_declined";
                } else if (description.contains("cvc") || description.contains("cvv")) {
                    return "invalid_cvc";
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to parse failure reason from error", ex);
        }
        return "processing_error";
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSuccess(Invoice invoice, String tenantId, String paymentId) {
        Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId()).orElseThrow();
        Plan plan = planRepository.findById(subscription.getPlanId()).orElseThrow();

        accessService.grantAccess(
                subscription.getTenantId(),
                subscription.getCustomerId(),
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
}
