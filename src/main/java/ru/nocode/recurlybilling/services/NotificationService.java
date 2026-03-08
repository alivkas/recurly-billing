package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.*;
import ru.nocode.recurlybilling.data.repositories.NotificationRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;
import ru.nocode.recurlybilling.components.notifications.senders.EmailSender;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    @Transactional
    public void sendUpcomingPaymentNotification(Subscription subscription, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        String recipientEmail = getCustomerEmail(subscription.getCustomerId(), tenant.getTenantId());

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customer_name", getCustomerName(subscription.getCustomerId(), tenant.getTenantId()));
        templateData.put("amount", plan.getPriceCents() / 100.0);
        templateData.put("currency", "RUB");
        templateData.put("next_billing_date", subscription.getNextBillingDate());
        templateData.put("plan_name", plan.getName());

        Notification notification = createNotification(
                subscription,
                "upcoming_payment",
                "email",
                recipientEmail,
                "Предстоящий платёж за подписку",
                "Здравствуйте, {customer_name}!\n\nВаш платёж на сумму {amount} ₽ за подписку «{plan_name}» будет списан {next_billing_date}.\n\nС уважением, команда {tenant_name}.",
                templateData,
                tenant.getName()
        );

        sendNotificationAsync(notification, templateData);
    }

    @Transactional
    public void sendPaymentSucceededNotification(Subscription subscription, Invoice invoice, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        String recipientEmail = getCustomerEmail(subscription.getCustomerId(), tenant.getTenantId());

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customer_name", getCustomerName(subscription.getCustomerId(), tenant.getTenantId()));
        templateData.put("amount", invoice.getAmountCents() / 100.0);
        templateData.put("currency", "RUB");
        templateData.put("payment_id", invoice.getPaymentId());
        templateData.put("plan_name", plan.getName());
        templateData.put("next_billing_date", subscription.getNextBillingDate());
        templateData.put("tenant_name", tenant.getName());

        Notification notification = createNotification(
                subscription,
                "payment_succeeded",
                "email",
                recipientEmail,
                "Платёж успешно проведён",
                "Здравствуйте, {customer_name}!\n\nВаш платёж на сумму {amount} ₽ за подписку «{plan_name}» успешно проведён.\nID платежа: {payment_id}\nСледующее списание: {next_billing_date}\n\nС уважением, команда {tenant_name}.",
                templateData,
                tenant.getName()
        );

        sendNotificationAsync(notification, templateData);
    }

    @Transactional
    public void sendPaymentFailedNotification(Subscription subscription, Invoice invoice, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        String recipientEmail = getCustomerEmail(subscription.getCustomerId(), tenant.getTenantId());

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customer_name", getCustomerName(subscription.getCustomerId(), tenant.getTenantId()));
        templateData.put("amount", invoice.getAmountCents() / 100.0);
        templateData.put("currency", "RUB");
        templateData.put("payment_id", invoice.getPaymentId());
        templateData.put("plan_name", plan.getName());
        templateData.put("retry_count", invoice.getAttemptCount() + 1);
        templateData.put("tenant_name", tenant.getName());

        Notification notification = createNotification(
                subscription,
                "payment_failed",
                "email",
                recipientEmail,
                "Неудачная попытка оплаты",
                "Здравствуйте, {customer_name}!\n\nПлатёж на сумму {amount} ₽ за подписку «{plan_name}» не прошёл.\nID платежа: {payment_id}\nПопытка #{retry_count}\n\nПожалуйста, проверьте данные карты или свяжитесь с поддержкой.\nС уважением, команда {tenant_name}.",
                templateData,
                tenant.getName()
        );

        sendNotificationAsync(notification, templateData);
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    @Transactional
    public void sendScheduledUpcomingPaymentNotifications() {
        LocalDate threeDaysFromNow = LocalDate.now().plusDays(3);
        LocalDate fourDaysFromNow = LocalDate.now().plusDays(4);

        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();

        for (String tenantId : tenantIds) {
            try {
                List<Subscription> subscriptions = subscriptionRepository
                        .findByTenantIdAndStatusAndNextBillingDateBefore(
                                tenantId,
                                "active",
                                fourDaysFromNow
                        ).stream()
                        .filter(s -> s.getNextBillingDate() != null &&
                                !s.getNextBillingDate().isBefore(threeDaysFromNow))
                        .collect(Collectors.toList());

                for (Subscription subscription : subscriptions) {
                    try {
                        Plan plan = getPlanForSubscription(subscription);
                        sendUpcomingPaymentNotification(subscription, plan);
                        log.info("Scheduled notification sent for subscription {}", subscription.getId());
                    } catch (Exception e) {
                        log.error("Failed to send notification for subscription {}", subscription.getId(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing notifications for tenant {}", tenantId, e);
            }
        }
    }

    @Async
    void sendNotificationAsync(Notification notification, Map<String, Object> templateData) {
        try {
            templateData.putIfAbsent("tenant_name", notification.getTenantId());

            String renderedBody = renderTemplate(notification.getBodyTemplate(), templateData);
            String renderedSubject = renderTemplate(notification.getSubject(), templateData);

            boolean sent = false;
            String errorMessage = null;

            if ("email".equals(notification.getChannel()) &&
                    notification.getRecipient() != null &&
                    !notification.getRecipient().isBlank()) {

                try {
                    emailSender.send(notification.getRecipient(), renderedSubject, renderedBody);
                    sent = true;
                } catch (Exception e) {
                    errorMessage = "Email send failed: " + e.getMessage();
                    log.warn("Failed to send email to {}: {}", notification.getRecipient(), e.getMessage());
                }
            } else {
                errorMessage = "Unsupported channel or empty recipient: " + notification.getChannel();
                log.warn("Cannot send notification: {}", errorMessage);
            }

            notification.setStatus(sent ? "sent" : "failed");
            notification.setSentAt(LocalDateTime.now());
            if (!sent && errorMessage != null) {
                notification.setErrorMessage(errorMessage);
            }
            notificationRepository.save(notification);

            if (sent) {
                log.info("Notification sent: id={}, channel={}, recipient={}",
                        notification.getId(), notification.getChannel(), notification.getRecipient());
            }
        } catch (Exception e) {
            log.error("Async notification sending failed: id={}", notification.getId(), e);
            notification.setStatus("failed");
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage("Internal error: " + e.getMessage());
            notificationRepository.save(notification);
        }
    }

    private Notification createNotification(
            Subscription subscription,
            String type,
            String channel,
            String recipient,
            String subject,
            String bodyTemplate,
            Map<String, Object> metadata,
            String tenantName) {

        if (metadata != null) {
            metadata.put("tenant_name", tenantName);
        }

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId(subscription.getTenantId());
        notification.setSubscriptionId(subscription.getId());
        notification.setType(type);
        notification.setChannel(channel);
        notification.setRecipient(recipient);
        notification.setSubject(subject);
        notification.setBodyTemplate(bodyTemplate);
        notification.setStatus("pending");
        notification.setCreatedAt(LocalDateTime.now());

        if (metadata != null && !metadata.isEmpty()) {
            notification.setMetadata(objectMapper.valueToTree(metadata));
        }

        return notificationRepository.save(notification);
    }

    private String renderTemplate(String template, Map<String, Object> data) {
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private Plan getPlanForSubscription(Subscription subscription) {
        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found for subscription"));
    }

    //TODO
    private String getCustomerEmail(UUID customerId, String tenantId) {
        return "student@example.com";
    }

    private String getCustomerName(UUID customerId, String tenantId) {
        return "Уважаемый клиент";
    }
}