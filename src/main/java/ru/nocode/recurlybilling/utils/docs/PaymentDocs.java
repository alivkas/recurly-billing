package ru.nocode.recurlybilling.utils.docs;

public final class PaymentDocs {

    public static final String TAG_DESCRIPTION = """
            Управление платежами и обработка уведомлений от платёжных систем.
            
            **Архитектура обработки платежей:**
            ```
            Клиент → Ваш фронтенд → Recurly API → YooKassa
                                          ↓
            YooKassa → Webhook → /api/v1/payments/webhook → Обновление статуса
            ```
            
            **Типы эндпоинтов:**
            
            | Эндпоинт | Авторизация | Назначение |
            |----------|-------------|------------|
            | `POST /webhook` | 🔐 Подпись YooKassa (`X-Signature`) | Приём уведомлений от платёжной системы |
            | `GET /{id}` | 🔑 `X-Tenant-ID` + `X-API-Key` | Получение информации о платеже |
            | `GET /success` | ❌ Публичный | Страница успешной оплаты (редирект) |
            
            **Безопасность вебхуков:**
            
            Все запросы от ЮKassa на `/webhook` должны содержать заголовок `X-Signature`.
            Это HMAC-SHA256 подпись тела запроса, вычисленная с использованием 
            вашего секретного ключа. Система автоматически проверяет подпись 
            и отклоняет неподписанные запросы.
            
            **Настройка в ЮKassa:**
            1. ЛК ЮKassa → Интеграция → Уведомления
            2. Укажите URL: `https://api.yoursite.com/api/v1/payments/webhook`
            3. Включите события: `payment.succeeded`, `payment.canceled`, `payment.waiting_for_capture`
            4. Скопируйте секретный ключ и добавьте в `application.properties`:
               ```properties
               yookassa.secret-key=your_secret_key_here
               ```
            """;

    public static final String WEBHOOK_SUMMARY = "🔔 Обработать уведомление от YooKassa";

    public static final String WEBHOOK_DESCRIPTION = """
            **Публичный эндпоинт** для приёма вебхуков от платёжной системы ЮKassa.
            
            **Как это работает:**
            1. ЮKassa отправляет POST-запрос с данными о событии платежа
            2. Заголовок `X-Signature` содержит HMAC-подпись для верификации
            3. Система проверяет подпись по секретному ключу
            4. Извлекает `shop_id` → находит соответствующий `tenant_id`
            5. Обновляет статус платежа и подписки в базе данных
            6. Возвращает `200 OK` (ЮKassa повторит запрос при ошибке)
            
            **Поддерживаемые события:**
            | Событие | Описание | Действие системы |
            |---------|----------|-----------------|
            | `payment.succeeded` | Платёж успешно проведён | Активация подписки, отправка чека |
            | `payment.canceled` | Платёж отменён | Обновление статуса подписки на `past_due` |
            | `payment.waiting_for_capture` | Требуется подтверждение | Ожидание ручного подтверждения (если настроено) |
            | `payment.refunded` | Возврат средств | Обновление истории платежей |
            
            **Формат запроса от ЮKassa:**
            ```json
            {
              "event": "payment.succeeded",
              "object": {
                "id": "payment_123456",
                "status": "succeeded",
                "amount": { "value": "1490.00", "currency": "RUB" },
                "recipient": { "account_id": "123456" },
                "metadata": { "subscription_id": "sub_abc123", "tenant_id": "6546tds" }
              }
            }
            ```
            
            **Важно:**
            - Эндпоинт **не требует** заголовков `X-Tenant-ID` / `X-API-Key`
            - Проверка осуществляется через криптографическую подпись
            - Ответ `200 OK` обязателен — иначе ЮKасса повторит запрос до 24 часов
            - Обработка должна быть идемпотентной (повторный вебхук не создаст дубликат)
            """;

    public static final String WEBHOOK_EXAMPLE = """
            {
              "event": "payment.succeeded",
              "object": {
                "id": "payment_123456",
                "status": "succeeded",
                "amount": {
                  "value": "1490.00",
                  "currency": "RUB"
                },
                "recipient": {
                  "account_id": "123456"
                },
                "payment_method": {
                  "type": "bank_card",
                  "card": {
                    "first6": "555555",
                    "last4": "4444",
                    "expiry_month": "12",
                    "expiry_year": "2025"
                  }
                },
                "metadata": {
                  "subscription_id": "sub_abc123",
                  "customer_id": "cust_ivan_2024"
                },
                "created_at": "2026-03-31T22:27:38.000Z"
              }
            }
            """;

    public static final String GET_SUMMARY = "🔎 Получить информацию о платеже";

    public static final String GET_DESCRIPTION = """
            Возвращает детальную информацию о платеже по его идентификатору.
            
            **Когда использовать:**
            - Отображение истории платежей в личном кабинете клиента
            - Диагностика проблем с оплатой (почему не активировалась подписка)
            - Сверка данных с платёжной системой при поддержке
            
            **Идентификатор платежа:**
            - Может быть как системным (`pay_abc123`), так и ID из ЮKassa (`payment_123456`)
            - Система автоматически определяет тип и ищет в соответствующей таблице
            
            **Что возвращается:**
            - Статус платежа и дата изменения
            - Сумма, валюта, способ оплаты
            - Связанные сущности: подписка, клиент, тенант
            - Ссылка на чек (если сформирован через онлайн-кассу)
            """;

    public static final String SUCCESS_SUMMARY = "✅ Страница успешной оплаты";

    public static final String SUCCESS_DESCRIPTION = """
            Простая страница для редиректа после успешной оплаты.
            
            **Настройка в ЮKassa:**
            В настройках магазина укажите:
            ```
            Return URL: https://yoursite.com/api/v1/payments/success
            ```
            
            После подтверждения платежа ЮKassa перенаправит клиента на этот адрес.
            
            **Для продакшена:**
            Рекомендуется заменить этот эндпоинт на редирект в ваш фронтенд:
            ```java
            @GetMapping("/success")
            public ResponseEntity<Void> success(@RequestParam String paymentId) {
                return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create("https://app.yoursite.com/payment/success?id=" + paymentId))
                    .build();
            }
            ```
            """;
}
