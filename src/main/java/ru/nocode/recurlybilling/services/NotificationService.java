package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.components.telegram.TelegramBot;
import ru.nocode.recurlybilling.data.entities.*;
import ru.nocode.recurlybilling.data.repositories.*;

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
    private final CustomerRepository customerRepository;
    private final TelegramBot telegramBot;
    private final EncryptionService encryptionService;

    @Value("${notifications.telegram.enabled:true}")
    private boolean telegramEnabled;

    @Value("${notifications.upcoming-days:3}")
    private int upcomingDays;

    @Transactional
    public void sendUpcomingPaymentNotification(Subscription subscription, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        Long telegramChatId = getCustomerTelegramChatId(subscription.getCustomerId(), tenant.getTenantId());

        if (telegramEnabled && telegramChatId != null) {
            String message = String.format("""
                💳 <b>Предстоящий платёж</b>
                
                Здравствуйте, %s!
                
                Ваш платёж на сумму <b>%s ₽</b> за подписку «%s» будет списан <b>%s</b>.
                
                С уважением,
                команда %s.
                """,
                    getCustomerName(subscription.getCustomerId(), tenant.getTenantId()),
                    plan.getPriceCents() / 100.0,
                    plan.getName(),
                    subscription.getNextBillingDate(),
                    tenant.getName()
            );

            Notification notification = createNotification(
                    subscription,
                    "upcoming_payment",
                    "telegram",
                    telegramChatId.toString(),
                    "Предстоящий платёж",
                    message
            );

            sendTelegramNotificationAsync(notification, message);
        }
    }

    @Transactional
    public void sendPaymentSucceededNotification(Subscription subscription, Invoice invoice, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        Long telegramChatId = getCustomerTelegramChatId(subscription.getCustomerId(), tenant.getTenantId());

        if (telegramEnabled && telegramChatId != null) {
            String message = String.format("""
                ✅ <b>Платёж успешен</b>
                
                Здравствуйте, %s!
                
                Ваш платёж на сумму <b>%s ₽</b> за подписку «%s» успешно проведён.
                
                ID платежа: <code>%s</code>
                Следующее списание: <b>%s</b>
                
                С уважением,
                команда %s.
                """,
                    getCustomerName(subscription.getCustomerId(), tenant.getTenantId()),
                    invoice.getAmountCents() / 100.0,
                    plan.getName(),
                    invoice.getPaymentId(),
                    subscription.getNextBillingDate(),
                    tenant.getName()
            );

            Notification notification = createNotification(
                    subscription,
                    "payment_succeeded",
                    "telegram",
                    telegramChatId.toString(),
                    "✅ Платёж успешен",
                    message
            );

            sendTelegramNotificationAsync(notification, message);
        }
    }

    @Transactional
    public void sendPaymentFailedNotification(Subscription subscription, Invoice invoice, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        Long telegramChatId = getCustomerTelegramChatId(subscription.getCustomerId(), tenant.getTenantId());

        if (telegramEnabled && telegramChatId != null) {
            String message = String.format("""
                ❌ <b>Неудачная попытка оплаты</b>
                
                Здравствуйте, %s!
                
                Платёж на сумму <b>%s ₽</b> за подписку «%s» не прошёл.
                
                ID платежа: <code>%s</code>
                Попытка: <b>#%d</b>
                
                Пожалуйста, проверьте данные карты или свяжитесь с поддержкой.
                
                С уважением,
                команда %s.
                """,
                    getCustomerName(subscription.getCustomerId(), tenant.getTenantId()),
                    invoice.getAmountCents() / 100.0,
                    plan.getName(),
                    invoice.getPaymentId(),
                    invoice.getAttemptCount() + 1,
                    tenant.getName()
            );

            Notification notification = createNotification(
                    subscription,
                    "payment_failed",
                    "telegram",
                    telegramChatId.toString(),
                    "❌ Неудачная попытка оплаты",
                    message
            );

            sendTelegramNotificationAsync(notification, message);
        }
    }

    private String getCustomerName(UUID customerId, String tenantId) {
        return customerRepository.findById(customerId)
                .map(c -> {
                    try {
                        return encryptionService.decrypt(c.getFullName());
                    } catch (Exception e) {
                        log.error("Failed to decrypt name for customer: {}", customerId, e);
                        return "Уважаемый клиент";
                    }
                })
                .orElse("Уважаемый клиент");
    }

    private Long getCustomerTelegramChatId(UUID customerId, String tenantId) {
        return customerRepository.findById(customerId)
                .map(Customer::getTelegramChatId)
                .orElse(null);
    }

    private Notification createNotification(
            Subscription subscription,
            String type,
            String channel,
            String recipient,
            String subject,
            String bodyTemplate) {

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

        return notificationRepository.save(notification);
    }

    @Async
    void sendTelegramNotificationAsync(Notification notification, String message) {
        try {
            Long chatId = Long.parseLong(notification.getRecipient());
            boolean sent = telegramBot.sendMessageWithHtml(chatId, message);

            notification.setStatus(sent ? "sent" : "failed");
            notification.setSentAt(LocalDateTime.now());
            if (!sent) {
                notification.setErrorMessage("Telegram API error");
            }
            notificationRepository.save(notification);

            log.info("📱 Telegram {} to chatId: {}", sent ? "sent" : "failed", chatId);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification", e);
            notification.setStatus("failed");
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Moscow")
    @Transactional
    public void sendScheduledUpcomingPaymentNotifications() {
        LocalDate targetDate = LocalDate.now().plusDays(upcomingDays);
        LocalDate nextDay = targetDate.plusDays(1);

        List<String> tenantIds = tenantRepository.findAllActiveTenantIds();

        for (String tenantId : tenantIds) {
            try {
                List<Subscription> subscriptions = subscriptionRepository
                        .findByTenantIdAndStatusAndNextBillingDateBefore(
                                tenantId,
                                "active",
                                nextDay
                        ).stream()
                        .filter(s -> s.getNextBillingDate() != null &&
                                !s.getNextBillingDate().isBefore(targetDate))
                        .collect(Collectors.toList());

                for (Subscription subscription : subscriptions) {
                    try {
                        Plan plan = planRepository.findById(subscription.getPlanId())
                                .orElseThrow();
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
}