package ru.nocode.recurlybilling.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.components.telegram.TelegramBot;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.entities.*;
import ru.nocode.recurlybilling.data.repositories.*;
import ru.nocode.recurlybilling.services.tenant.EncryptionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final JavaMailSender javaMailSender;
    private final EncryptionService encryptionService;
    private final StudentAuthService studentAuthService;
    private final InvoiceRepository invoiceRepository;
    private final ObjectProvider<PaymentService> paymentServiceProvider;

    @Value("${notifications.telegram.enabled:true}")
    private boolean telegramEnabled;

    @Value("${notifications.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${spring.mail.username}")
    private String emailUsername;

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

        if (emailEnabled) {
            String email = getCustomerEmail(subscription.getCustomerId());
            if (email != null && !email.isBlank()) {
                String subject = "Предстоящий платёж за подписку";
                String htmlContent = buildUpcomingPaymentMessage(subscription, plan, tenant, true);
                Notification notification = createNotification(
                        subscription, "upcoming_payment", "email",
                        email, subject, htmlContent);
                sendEmailNotificationAsync(notification, subject, htmlContent, email);
            }
        }
    }

    @Transactional
    public void sendPaymentSucceededNotification(Subscription subscription, Invoice invoice, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        Customer customer = customerRepository.findById(subscription.getCustomerId())
                .orElseThrow(() -> new IllegalStateException("Customer not found for subscription"));

        Long telegramChatId = getCustomerTelegramChatId(subscription.getCustomerId(), tenant.getTenantId());

        if (telegramEnabled && telegramChatId != null) {
            String tempCode = studentAuthService.generateTemporaryCode(
                    subscription.getTenantId(),
                    customer.getExternalId()
            );

            String message = String.format("""
            ✅ <b>Платёж успешен</b>
            
            Здравствуйте, %s!
            
            Ваш платёж на сумму <b>%s ₽</b> за подписку «%s» успешно проведён.
            
            📌 <b>Ваш код для входа в личный кабинет:</b> <code>%s</code>
            Действует 24 часа. Используйте его на странице входа.
            
            ID платежа: <code>%s</code>
            Следующее списание: <b>%s</b>
            
            С уважением,
            команда %s.
            """,
                    getCustomerName(subscription.getCustomerId(), tenant.getTenantId()),
                    invoice.getAmountCents() / 100.0,
                    plan.getName(),
                    tempCode,
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

        if (emailEnabled) {
            String email = getCustomerEmail(subscription.getCustomerId());
            if (email != null && !email.isBlank()) {
                String tempCode = studentAuthService.generateTemporaryCode(
                        subscription.getTenantId(), customer.getExternalId());
                String subject = "Платёж успешен — доступ открыт";
                String htmlContent = buildPaymentSucceededMessage(subscription, invoice, plan, tenant, tempCode, true);
                Notification notification = createNotification(
                        subscription, "payment_succeeded", "email",
                        email, subject, htmlContent);
                sendEmailNotificationAsync(notification, subject, htmlContent, email);
            }
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

        if (emailEnabled) {
            String email = getCustomerEmail(subscription.getCustomerId());
            if (email != null && !email.isBlank()) {
                String subject = "Не удалось списать платёж";
                String htmlContent = buildPaymentFailedMessage(subscription, invoice, plan, tenant, true);
                Notification notification = createNotification(
                        subscription, "payment_failed", "email",
                        email, subject, htmlContent);
                sendEmailNotificationAsync(notification, subject, htmlContent, email);
            }
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

    private String getCustomerEmail(UUID customerId) {
        return customerRepository.findById(customerId)
                .map(c -> {
                    try {
                        return encryptionService.decrypt(c.getEmail());
                    } catch (Exception e) {
                        log.error("Failed to decrypt email for customer: {}", customerId, e);
                        return null;
                    }
                })
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
    public void sendTelegramNotificationAsync(Notification notification, String message) {
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

    @Transactional
    public void sendTrialEndingNotification(Subscription subscription, Plan plan) {
        Tenant tenant = tenantRepository.findById(subscription.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        Long telegramChatId = getCustomerTelegramChatId(subscription.getCustomerId(), tenant.getTenantId());

        if (telegramEnabled && telegramChatId != null) {
            String paymentUrl = getOrCreateTrialPaymentLink(subscription);

            String message = String.format("""
            ⏳ <b>Пробный период заканчивается</b>
            
            Здравствуйте, %s!
            
            Ваш пробный период для курса «%s» заканчивается <b>%s</b>.
            
            %s
            
            🔗 <a href="%s">💳 Оплатить и продлить доступ</a>
            
            Если вы не хотите продолжать подписку — отмените её до окончания пробного периода.
            
            С уважением,
            команда %s.
            """,
                    getCustomerName(subscription.getCustomerId(), tenant.getTenantId()),
                    plan.getName(),
                    formatTrialEndDate(subscription.getTrialEnd().atStartOfDay()),
                    buildPaymentInstructionBlock(subscription),
                    paymentUrl != null ? paymentUrl : "#",
                    tenant.getName()
            );

            Notification notification = createNotification(
                    subscription,
                    "trial_ending",
                    "telegram",
                    telegramChatId.toString(),
                    "⏳ Пробный период заканчивается",
                    message
            );

            sendTelegramNotificationAsync(notification, message);

            log.info("Trial ending notification with payment link sent: subscription={}, url={}",
                    subscription.getId(), paymentUrl != null);
        }

        if (emailEnabled) {
            String paymentUrl = getOrCreateTrialPaymentLink(subscription);
            String email = getCustomerEmail(subscription.getCustomerId());
            if (email != null && !email.isBlank()) {
                String subject = "Пробный период заканчивается — оплатите подписку";
                String htmlContent = buildTrialEndingMessage(subscription, plan, tenant, paymentUrl, true);
                Notification notification = createNotification(
                        subscription, "trial_ending", "email",
                        email, subject, htmlContent);
                sendEmailNotificationAsync(notification, subject, htmlContent, email);
            }
        }
    }

    private String getOrCreateTrialPaymentLink(Subscription subscription) {
        try {
            Optional<Invoice> existingInvoice = invoiceRepository
                    .findBySubscriptionIdAndStatus(subscription.getId(), "pending_deferred");

            if (existingInvoice.isPresent() && existingInvoice.get().getConfirmationUrl() != null) {
                return existingInvoice.get().getConfirmationUrl();
            }

            String idempotencyKey = "trial_notify_" + subscription.getId() + "_" + System.currentTimeMillis();
            PaymentResponse payment = paymentServiceProvider.getObject().createDeferredPaymentForTrial(subscription, idempotencyKey);

            return payment.confirmationUrl();

        } catch (Exception e) {
            log.error("Failed to generate payment link for subscription {}", subscription.getId(), e);
            return null;
        }
    }

    private String buildPaymentInstructionBlock(Subscription subscription) {
        boolean hasBoundCard = subscription.getPaymentMethodId() != null
                && !subscription.getPaymentMethodId().isBlank();

        if (hasBoundCard) {
            return "✅ У вас есть привязанная карта. При оплате вы сможете использовать её или добавить новую.";
        }
        return "❗ Для продолжения доступа необходимо добавить способ оплаты.";
    }

    private String formatTrialEndDate(LocalDateTime trialEnd) {
        if (trialEnd == null) return "в ближайшее время";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'в' HH:mm", new Locale("ru"));
        return trialEnd.format(formatter);
    }

    private String buildUpcomingPaymentMessage(Subscription subscription, Plan plan, Tenant tenant, boolean isEmail) {
        String customerName = getCustomerName(subscription.getCustomerId(), tenant.getTenantId());
        double amount = plan.getPriceCents() / 100.0;
        String nextBilling = subscription.getNextBillingDate() != null
                ? subscription.getNextBillingDate().toString() : "не запланировано";
            return String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #4A90E2; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .amount { font-size: 24px; font-weight: bold; color: #2E7D32; }
                    .footer { background: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                </style></head>
                <body>
                    <div class="header"><h2>💳 Предстоящий платёж</h2></div>
                    <div class="content">
                        <p>Здравствуйте, <b>%s</b>!</p>
                        <p>Ваш платёж на сумму <span class="amount">%s ₽</span> за подписку «<b>%s</b>» будет списан <b>%s</b>.</p>
                        <p>Если у вас есть вопросы — напишите нам в поддержку.</p>
                    </div>
                    <div class="footer">С уважением, команда %s</div>
                </body>
                </html>
                """, customerName, amount, plan.getName(), nextBilling, tenant.getName());

    }

    private String buildPaymentSucceededMessage(Subscription subscription, Invoice invoice, Plan plan, Tenant tenant, String tempCode, boolean isEmail) {
        String customerName = getCustomerName(subscription.getCustomerId(), tenant.getTenantId());
        double amount = invoice.getAmountCents() / 100.0;
        String nextBilling = subscription.getNextBillingDate() != null
                ? subscription.getNextBillingDate().toString() : "не запланировано";

            return String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #2E7D32; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .code { background: #f0f0f0; padding: 10px 15px; font-family: monospace; font-size: 18px; letter-spacing: 3px; border-radius: 4px; display: inline-block; margin: 10px 0; }
                    .footer { background: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                </style></head>
                <body>
                    <div class="header"><h2>✅ Платёж успешен</h2></div>
                    <div class="content">
                        <p>Здравствуйте, <b>%s</b>!</p>
                        <p>Ваш платёж на сумму <b>%s ₽</b> за подписку «<b>%s</b>» успешно проведён.</p>
                        <p><b>Ваш код для входа в личный кабинет:</b></p>
                        <div class="code">%s</div>
                        <p><i>Код действует 24 часа. Используйте его на странице входа.</i></p>
                        <p>ID платежа: <code>%s</code><br>
                        Следующее списание: <b>%s</b></p>
                    </div>
                    <div class="footer">С уважением, команда %s</div>
                </body>
                </html>
                """, customerName, amount, plan.getName(), tempCode, invoice.getPaymentId(), nextBilling, tenant.getName());
    }

    private String buildPaymentFailedMessage(Subscription subscription, Invoice invoice, Plan plan, Tenant tenant, boolean isEmail) {
        String customerName = getCustomerName(subscription.getCustomerId(), tenant.getTenantId());
        double amount = invoice.getAmountCents() / 100.0;
        int attempt = invoice.getAttemptCount() + 1;

            return String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #D32F2F; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 12px; margin: 15px 0; }
                    .footer { background: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                </style></head>
                <body>
                    <div class="header"><h2>❌ Неудачная попытка оплаты</h2></div>
                    <div class="content">
                        <p>Здравствуйте, <b>%s</b>!</p>
                        <p>Платёж на сумму <b>%s ₽</b> за подписку «<b>%s</b>» не прошёл.</p>
                        <p>ID платежа: <code>%s</code><br>
                        Попытка: <b>#%d</b></p>
                        <div class="warning">
                            <p><b>Что делать:</b></p>
                            <ul>
                                <li>Проверьте данные карты (номер, срок, CVC)</li>
                                <li>Убедитесь, что на карте достаточно средств</li>
                                <li>Попробуйте добавить другой способ оплаты</li>
                            </ul>
                        </div>
                        <p>Если проблема не решается — напишите в поддержку.</p>
                    </div>
                    <div class="footer">С уважением, команда %s</div>
                </body>
                </html>
                """, customerName, amount, plan.getName(), invoice.getPaymentId(), attempt, tenant.getName());
    }

    private String buildTrialEndingMessage(Subscription subscription, Plan plan, Tenant tenant, String paymentUrl, boolean isEmail) {
        String customerName = getCustomerName(subscription.getCustomerId(), tenant.getTenantId());
        String trialEnd = formatTrialEndDate(subscription.getTrialEnd().atStartOfDay());
        String paymentInstruction = buildPaymentInstructionBlock(subscription);
        String actionText = paymentUrl != null && !paymentUrl.isBlank()
                ? String.format("🔗 <a href=\"%s\">💳 Оплатить и продлить доступ</a>", paymentUrl)
                : "Обратитесь в поддержку для завершения оплаты.";

            return String.format("""
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .header { background: #FF9800; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .deadline { background: #fff3e0; border-left: 4px solid #FF9800; padding: 12px; margin: 15px 0; font-weight: bold; }
                    .button { display: inline-block; background: #4A90E2; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 15px 0; }
                    .footer { background: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                </style></head>
                <body>
                    <div class="header"><h2>⏳ Пробный период заканчивается</h2></div>
                    <div class="content">
                        <p>Здравствуйте, <b>%s</b>!</p>
                        <p>Ваш пробный период для курса «<b>%s</b>» заканчивается <b>%s</b>.</p>
                        <div class="deadline">%s</div>
                        %s
                        <p><i>Если вы не хотите продолжать подписку — отмените её до окончания пробного периода.</i></p>
                    </div>
                    <div class="footer">С уважением, команда %s</div>
                </body>
                </html>
                """, customerName, plan.getName(), trialEnd, paymentInstruction, actionText, tenant.getName());
    }

    @Async
    public void sendEmailNotificationAsync(Notification notification, String subject, String htmlContent, String recipient) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("lyamzy@yandex.ru");
            helper.setReplyTo("support@your-domain.ru");

            javaMailSender.send(message);

            notification.setStatus("sent");
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

            log.info("✉️ Email sent to: {}", recipient);
        } catch (jakarta.mail.AuthenticationFailedException e) {
            log.error("❌ SMTP Authentication failed: check username/password for {}", recipient, e);
            notification.setStatus("failed");
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage("Authentication failed: " + e.getMessage());
            notificationRepository.save(notification);

        } catch (jakarta.mail.MessagingException e) {
            log.error("❌ Failed to send email to {}: {}", recipient, e.getMessage(), e);
            notification.setStatus("failed");
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending email to: {}", recipient, e);
            notification.setStatus("failed");
            notification.setSentAt(LocalDateTime.now());
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
        }
    }
}