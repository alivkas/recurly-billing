package ru.nocode.recurlybilling.components.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaClient;

@Slf4j
@Component("yookassaHealth")
@RequiredArgsConstructor
public class YooKassaHealthIndicator implements HealthIndicator {

    private final YooKassaClient yooKassaClient;

    @Override
    public Health health() {
        try {
            boolean isAvailable = yooKassaClient.isHealthy();

            if (isAvailable) {
                return Health.up()
                        .withDetail("service", "YooKassa")
                        .withDetail("status", "available")
                        .withDetail("checked_at", java.time.LocalDateTime.now().toString())
                        .build();
            } else {
                return Health.down()
                        .withDetail("service", "YooKassa")
                        .withDetail("status", "unavailable")
                        .withDetail("checked_at", java.time.LocalDateTime.now().toString())
                        .build();
            }
        } catch (Exception e) {
            log.warn("YooKassa health check failed", e);
            return Health.down(e)
                    .withDetail("service", "YooKassa")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("checked_at", java.time.LocalDateTime.now().toString())
                    .build();
        }
    }
}