package ru.nocode.recurlybilling.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nocode.recurlybilling.components.metrics.BusinessMetrics;
import ru.nocode.recurlybilling.services.NotificationService;
import ru.nocode.recurlybilling.services.SubscriptionService;

@RestController()
@RequiredArgsConstructor
public class TestController {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final BusinessMetrics businessMetrics;
    private final JavaMailSender javaMailSender;

    @GetMapping("/test-billing")
    public ResponseEntity<String> triggerBilling() {
        subscriptionService.processBillingForTenant("moscow_digital");
        return ResponseEntity.ok("Billing triggered");
    }

    @GetMapping("/test-trial-end")
    public ResponseEntity<String> testTrialEnd() {
        subscriptionService.processScheduledBilling();
        return ResponseEntity.ok("Trial end processing triggered");
    }

    @GetMapping("/test-notify")
    public ResponseEntity<String> testNotify() {
        notificationService.sendScheduledUpcomingPaymentNotifications();
        return ResponseEntity.ok("Notificated");
    }

    @GetMapping("/test-upcoming")
    public ResponseEntity<String> testUpcoming() {
        subscriptionService.sendTrialEndingNotifications();
        return ResponseEntity.ok("Notificated");
    }

    @GetMapping("/trigger-metrics")
    public ResponseEntity<String> triggerMetrics() {
        businessMetrics.recordPaymentSuccess("test-tenant", 10000L, "RUB");
        businessMetrics.recordSubscriptionCreated("test-tenant", "test-plan", false);
        return ResponseEntity.ok("Metrics triggered!");
    }

    @GetMapping("/test-email")
    public ResponseEntity<String> testEmail() {
        try {
            ((JavaMailSenderImpl) javaMailSender).testConnection();
            return ResponseEntity.ok("✅ SMTP connection successful!");
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("❌ Connection failed: " + e.getMessage());
        }
    }
}
