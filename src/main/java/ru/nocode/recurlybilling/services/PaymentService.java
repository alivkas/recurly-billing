package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    @Transactional
    public PaymentResponse createPaymentForSubscription(Subscription subscription, String idempotencyKey) {
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

        YooKassaPaymentResponse response;
        try {
            YooKassaPaymentRequest request = buildYooKassaRequest(savedInvoice, subscription, plan);
            response = yooKassaClient.createPayment(request, idempotencyKey);
        } catch (HttpClientErrorException e) {
            handleYooKassaClientError(e, savedInvoice);
            throw new RuntimeException("Payment rejected by YooKassa: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            savedInvoice.setStatus("failed");
            savedInvoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(savedInvoice);
            throw new RuntimeException("YooKassa unavailable", e);
        }

        String mappedStatus = mapYooKassaStatus(response.getStatus());
        savedInvoice.setPaymentId(response.getId());
        savedInvoice.setStatus(mappedStatus);
        savedInvoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(savedInvoice);

        return new PaymentResponse(
                response.getId(),
                mappedStatus,
                response.getConfirmation() != null ? response.getConfirmation().getConfirmationUrl() : null,
                plan.getPriceCents(),
                "RUB",
                response.getCreatedAt()
        );
    }

    @Transactional
    public void handleYooKassaWebhook(String paymentId, String status, Map<String, Object> metadata) {
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice with paymentId '" + paymentId + "' not found"));

        String oldStatus = invoice.getStatus();
        String mappedStatus = mapYooKassaStatus(status);
        invoice.setStatus(mappedStatus);
        invoice.setUpdatedAt(LocalDateTime.now());

        if (metadata != null && !metadata.isEmpty()) {
            invoice.setMetadata(objectMapper.valueToTree(metadata));
        }

        if ("paid".equals(mappedStatus) && !"paid".equals(oldStatus)) {
            extendSubscriptionPeriod(invoice);
        }

        invoiceRepository.save(invoice);
        log.info("Webhook processed: paymentId={}, oldStatus={}, newStatus={}", paymentId, oldStatus, mappedStatus);
    }

    private void extendSubscriptionPeriod(Invoice invoice) {
        Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                .orElseThrow(() -> new IllegalStateException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        if (subscription.getNextBillingDate() != null) {
            LocalDate newPeriodStart = subscription.getCurrentPeriodEnd().plusDays(1);
            LocalDate newPeriodEnd = calculateNextPeriodEnd(newPeriodStart, plan);

            subscription.setCurrentPeriodStart(newPeriodStart);
            subscription.setCurrentPeriodEnd(newPeriodEnd);
            subscription.setNextBillingDate(newPeriodEnd.plusDays(1));
            subscription.setUpdatedAt(LocalDateTime.now());

            subscriptionRepository.save(subscription);
            log.info("Subscription extended: id={}, newPeriodEnd={}", subscription.getId(), newPeriodEnd);
        } else {
            log.info("Subscription is one-time (semester/course), no extension needed: id={}", subscription.getId());
        }
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
        request.setAmount(new YooKassaPaymentRequest.Amount((long) amountRub.doubleValue(), "RUB"));

        request.setPaymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : "bank_card");
        request.setDescription("Оплата подписки #" + invoice.getId());

        String returnUrl = environment.getProperty("payment.return-url",
                "https://default.example.com/payment/success?tenant=" + subscription.getTenantId());
        request.setConfirmation(new YooKassaPaymentRequest.Confirmation(returnUrl));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId().toString());
        metadata.put("tenantId", subscription.getTenantId());
        metadata.put("invoiceId", invoice.getId().toString());
        metadata.put("planCode", plan.getCode());
        request.setMetadata(metadata);

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
