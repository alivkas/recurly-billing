package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.repositories.InvoiceRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PlanRepository planRepository;

    @Transactional
    public Invoice createPaymentForSubscription(Subscription subscription) {
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found"));

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setAmountCents(plan.getPriceCents());
        invoice.setStatus("pending");
        invoice.setPaymentMethod("card"); // TODO !!!
        invoice.setAttemptCount(0);
        invoice.setCreatedAt(LocalDateTime.now());

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void handleYooKassaWebhook(String eventType, String paymentId, String status) {
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        invoice.setStatus(status);
        invoice.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        if ("paid".equals(status)) {
            // TODO
        }
    }
}
