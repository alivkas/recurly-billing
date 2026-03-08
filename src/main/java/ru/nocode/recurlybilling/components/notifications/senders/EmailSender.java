package ru.nocode.recurlybilling.components.notifications.senders;

public interface EmailSender {
    void send(String to, String subject, String body);
}
