package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaClient;
import ru.nocode.recurlybilling.data.dto.request.PaymentCreateRequest;
import ru.nocode.recurlybilling.data.dto.request.YooKassaPaymentRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.YooKassaPaymentResponse;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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

    @Transactional
    public PaymentResponse createPaymentForSubscription(Subscription subscription) {
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found for subscription"));

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setAmountCents(plan.getPriceCents());
        invoice.setStatus("pending");
        invoice.setPaymentMethod(subscription.getPaymentMethod());
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        YooKassaPaymentRequest request = buildYooKassaRequest(savedInvoice, subscription, plan);
        YooKassaPaymentResponse response = yooKassaClient.createPayment(request);

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
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        UUID subscriptionId = UUID.fromString(request.subscriptionId());
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscriptionId);
        invoice.setAmountCents(request.amountCents() != null ? request.amountCents() : plan.getPriceCents());
        invoice.setStatus("pending");
        invoice.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "bank_card");
        invoice.setDescription(request.description() != null ? request.description() : "Оплата подписки");
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);

        YooKassaPaymentRequest yooRequest = new YooKassaPaymentRequest();
        yooRequest.setAmount(new YooKassaPaymentRequest.Amount(
                (long) new BigDecimal(invoice.getAmountCents()).divide(BigDecimal.valueOf(100)).doubleValue(),
                "RUB"
        ));
        yooRequest.setPaymentMethod(invoice.getPaymentMethod());
        yooRequest.setDescription(invoice.getDescription());
        yooRequest.setConfirmation(new YooKassaPaymentRequest.Confirmation(
                request.returnUrl() != null ? request.returnUrl() : "https://default-return-url.com"
        ));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscriptionId.toString());
        metadata.put("tenantId", subscription.getTenantId());
        metadata.put("invoiceId", invoice.getId().toString());
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        yooRequest.setMetadata(metadata);

        YooKassaPaymentResponse response = yooKassaClient.createPayment(yooRequest);

        String mappedStatus = mapYooKassaStatus(response.getStatus());
        savedInvoice.setPaymentId(response.getId());
        savedInvoice.setStatus(mappedStatus);
        savedInvoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(savedInvoice);

        return new PaymentResponse(
                response.getId(),
                mappedStatus,
                response.getConfirmation() != null ? response.getConfirmation().getConfirmationUrl() : null,
                invoice.getAmountCents(),
                "RUB",
                LocalDateTime.now()
        );
    }

    @Transactional
    public void handleYooKassaWebhook(String paymentId, String status, Map<String, Object> metadata) {
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice with paymentId '" + paymentId + "' not found"));
        // TODO ПРОВЕРКА ПОДПИСИ
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
        log.info("Webhook processed: paymentId={}, oldStatus={}, newStatus={}, metadata={}",
                paymentId, oldStatus, mappedStatus, metadata);
    }

    @Transactional
    public void retryFailedPayment(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (!"failed".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not in 'failed' status");
        }

        if (invoice.getAttemptCount() >= 3) {
            throw new IllegalStateException("Max retry attempts reached");
        }

        invoice.setAttemptCount(invoice.getAttemptCount() + 1);
        invoice.setStatus("pending");
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        Subscription subscription = subscriptionRepository.findById(invoice.getSubscriptionId())
                .orElseThrow(() -> new IllegalStateException("Subscription not found"));

        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        YooKassaPaymentRequest request = buildYooKassaRequest(invoice, subscription, plan);
        YooKassaPaymentResponse response = yooKassaClient.createPayment(request);

        String mappedStatus = mapYooKassaStatus(response.getStatus());
        invoice.setPaymentId(response.getId());
        invoice.setStatus(mappedStatus);
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        log.info("Retry payment succeeded: invoiceId={}, attempt={}, newPaymentId={}",
                invoiceId, invoice.getAttemptCount(), response.getId());
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

    private YooKassaPaymentRequest buildYooKassaRequest(Invoice invoice, Subscription subscription, Plan plan) {
        YooKassaPaymentRequest request = new YooKassaPaymentRequest();

        request.setAmount(new YooKassaPaymentRequest.Amount(
                (long) new BigDecimal(invoice.getAmountCents()).divide(BigDecimal.valueOf(100)).doubleValue(),
                "RUB"
        ));

        request.setPaymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : "bank_card");
        request.setDescription("Оплата подписки #" + invoice.getId());

        String returnUrl = getReturnUrlForTenant(subscription.getTenantId());
        request.setConfirmation(new YooKassaPaymentRequest.Confirmation(returnUrl));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("subscriptionId", subscription.getId().toString());
        metadata.put("tenantId", subscription.getTenantId());
        metadata.put("invoiceId", invoice.getId().toString());
        metadata.put("planCode", plan.getCode());
        request.setMetadata(metadata);

        return request;
    }

    private String getReturnUrlForTenant(String tenantId) {
        return "https://app.example.com/payment/success?tenant=" + tenantId;
    }

    private LocalDate calculateNextPeriodEnd(LocalDate start, Plan plan) {
        return switch (plan.getInterval()) {
            case "month" -> {
                yield start.plusMonths(plan.getIntervalCount());
            }
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
