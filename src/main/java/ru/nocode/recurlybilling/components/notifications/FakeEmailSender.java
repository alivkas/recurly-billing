package ru.nocode.recurlybilling.components.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.nocode.recurlybilling.components.notifications.senders.EmailSender;

@Slf4j
@Component
@Profile("prod")
public class FakeEmailSender implements EmailSender {
    @Override
    public void send(String to, String subject, String body) {
        log.info("📧 FAKE EMAIL SENT\nTo: {}\nSubject: {}\nBody:\n{}", to, subject, body);
    }
}
