package ru.nocode.recurlybilling.components.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.entities.Subscription;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import ru.nocode.recurlybilling.data.repositories.SubscriptionRepository;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;
import ru.nocode.recurlybilling.services.AccessService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot extends TelegramLongPollingBot {

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final AccessService accessService;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message = update.getMessage();
                long chatId = message.getChatId();
                String text = message.getText().trim();
                User from = message.getFrom();
                String username = from.getUserName();

                log.info("📩 Telegram message received: chatId={}, username={}, text='{}'",
                        chatId, username, text);
                if (text.startsWith("/start")) {
                    handleStartCommand(chatId, username, from.getFirstName(), text);
                } else if ("/status".equalsIgnoreCase(text)) {
                    handleStatusCommand(chatId, username);
                } else if ("/cancel".equalsIgnoreCase(text)) {
                    handleCancelCommand(chatId, username);
                }
                else {
                    sendHelpMessage(chatId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update", e);
        }
    }

    private void handleStartCommand(long chatId, String username, String firstName, String fullCommand) {
        String[] parts = fullCommand.split("\\s+");
        String tenantId = parts.length > 1 ? parts[1].trim() : null;

        if (tenantId == null || tenantId.isEmpty()) {
            String reply = """
                ❌ Укажите идентификатор учебного заведения.
                
                Пример команды:
                /start moscow_digital
                
                Спросите у администратора ваш идентификатор.
                """;
            sendMessage(chatId, reply);
            return;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty() || !tenantOpt.get().getIsActive()) {
            sendMessage(chatId, "❌ Учебное заведение с идентификатором '" + tenantId + "' не найдено.");
            return;
        }

        if (username == null || username.isEmpty()) {
            String reply = """
                ❌ У вас не установлен публичный username в Telegram.
                
                Чтобы получать уведомления:
                1. Настройки → «Изменить профиль» → «Имя пользователя»
                2. Установите имя (начинается с @)
                3. Перезапустите бота: /start %s
                
                После этого мы привяжем ваш аккаунт к системе оплаты.
                """.formatted(tenantId);
            sendMessage(chatId, reply);
            return;
        }

        Optional<Customer> customerOpt = customerRepository
                .findByTenantIdAndTelegramUsernameIgnoreCase(tenantId, username);

        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setTelegramChatId(chatId);
            customerRepository.save(customer);

            String reply = String.format("""
                ✅ Аккаунт привязан к %s!
                
                Теперь вы будете получать уведомления о платежах:
                • За 3 дня до списания
                • Об успешной оплате
                • О неудачных попытках
                
                Для проверки статуса: /status
                """, tenantOpt.get().getName());

            sendMessage(chatId, reply);
            log.info("✅ Telegram account linked: tenant={}, username={}, chatId={}, customerId={}",
                    tenantId, username, chatId, customer.getId());
        } else {
            String reply = String.format("""
                ❌ Аккаунт с username @%s не найден в системе %s.
                
                Проверьте:
                1. Вы указали верный идентификатор: %s
                2. Вы зарегистрированы в учебной платформе с этим username
                
                Текущий username в Telegram: @%s
                """, username, tenantOpt.get().getName(), tenantId, username);

            sendMessage(chatId, reply);
            log.warn("❌ Customer not found for tenant={}, username={}", tenantId, username);
        }
    }

    private void handleStatusCommand(long chatId, String username) {
        if (username == null || username.isEmpty()) {
            sendMessage(chatId,
                    "❌ У вас не установлен публичный username в Telegram.\n\n" +
                            "Установите его и отправьте /start <идентификатор>");
            return;
        }

        Optional<Customer> customerOpt = customerRepository
                .findByTenantIdAndTelegramUsernameIgnoreCase("%", username) // ← НЕТ! Так нельзя!
                .stream()
                .filter(c -> c.getTelegramChatId() != null && c.getTelegramChatId() == chatId)
                .findFirst();

        Optional<Customer> byChatId = customerRepository
                .findByTelegramChatId(chatId);

        if (byChatId.isPresent() && byChatId.get().getTelegramChatId() != null) {
            Customer customer = byChatId.get();
            Optional<Tenant> tenantOpt = tenantRepository.findById(customer.getTenantId());
            String tenantName = tenantOpt.map(Tenant::getName).orElse("учебное заведение");

            String reply = String.format("""
                ✅ Аккаунт привязан к %s
                
                • Telegram username: @%s
                • Статус: активен
                • Уведомления: включены
                
                Вы будете получать уведомления о платежах в этом чате.
                """, tenantName, username);
            sendMessage(chatId, reply);
        } else {
            sendMessage(chatId,
                    "⚠️ Аккаунт не привязан к системе оплаты.\n\n" +
                            "Отправьте /start <идентификатор_учебного_заведения>\n" +
                            "Пример: /start moscow_digital");
        }
    }

    private void sendHelpMessage(long chatId) {
        String reply = """
            💳 Я бот системы уведомлений о платежах
            
            Чтобы привязать аккаунт:
            1. Узнайте идентификатор вашего учебного заведения у администратора
            2. Отправьте команду:
               /start <идентификатор>
            
            Пример:
            /start moscow_digital
            
            После привязки вы будете получать уведомления:
            • О предстоящем списании (за 3 дня)
            • Об успешной оплате
            • О проблемах с платежом
            """;
        sendMessage(chatId, reply);
    }

    public boolean sendMessage(long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            execute(message);
            log.info("✅ Telegram message sent to chatId: {}", chatId);
            return true;
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send Telegram message to chatId: {}", chatId, e);
            return false;
        }
    }

    public boolean sendMessageWithHtml(long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("HTML");
            execute(message);
            return true;
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send Telegram message with HTML to chatId: {}", chatId, e);
            return false;
        }
    }

    private void handleCancelCommand(long chatId, String username) {
        if (username == null || username.isEmpty()) {
            sendMessage(chatId,
                    "❌ У вас не установлен публичный username в Telegram.\n\n" +
                            "Установите его в настройках профиля и отправьте /start");
            return;
        }

        // Ищем клиента по username (временно хардкодим тенант)
        Optional<Customer> customerOpt = customerRepository
                .findByTenantIdAndTelegramUsernameIgnoreCase("moscow_digital", username);

        if (customerOpt.isEmpty() || customerOpt.get().getTelegramChatId() == null) {
            sendMessage(chatId,
                    "⚠️ Ваш аккаунт не привязан к системе оплаты.\n\n" +
                            "Отправьте /start для привязки.");
            return;
        }

        Customer customer = customerOpt.get();
        List<Subscription> subscriptions = subscriptionRepository
                .findByTenantIdAndCustomerExternalId("moscow_digital", customer.getExternalId());

        Subscription activeSub = subscriptions.stream()
                .filter(s -> "active".equals(s.getStatus()) || "trialing".equals(s.getStatus()))
                .findFirst()
                .orElse(null);

        if (activeSub == null) {
            sendMessage(chatId, "❌ У вас нет активной подписки для отмены.");
            return;
        }

        activeSub.setStatus("cancelled");
        activeSub.setCancelAt(LocalDate.now());
        subscriptionRepository.save(activeSub);

        Plan plan = planRepository.findById(activeSub.getPlanId()).orElseThrow();
        accessService.revokeAccessImmediately(UUID.fromString(customer.getExternalId()), plan.getCode());

        String message = String.format("""
        ⚠️ <b>Подписка отменена</b>
        
        Доступ к курсу «%s» закрыт немедленно.
        Спасибо за использование сервиса!
        """, plan.getName());

        sendMessageWithHtml(chatId, message);
        log.info("Subscription cancelled via Telegram for customer {}", customer.getExternalId());
    }
}
