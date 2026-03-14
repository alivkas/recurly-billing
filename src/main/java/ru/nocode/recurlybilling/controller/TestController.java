package ru.nocode.recurlybilling.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nocode.recurlybilling.services.NotificationService;
import ru.nocode.recurlybilling.services.SubscriptionService;

@RestController()
@RequiredArgsConstructor
public class TestController {

    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

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

//    @GetMapping("/test-notify")
//    public ResponseEntity<String> testNotify() {
//        notificationService.sendPaymentReminderNotifications();
//        return ResponseEntity.ok("Notificated");
//    }
}
