package ru.nocode.recurlybilling.utils.docs;

public final class StudentSubscriptionDocs {

    public static final String TAG_DESCRIPTION = """
            Управление подпиской из личного кабинета студента.
            
            **Зачем нужен этот эндпоинт?**
            
            `StudentSubscriptionController` позволяет студенту самостоятельно:
            - 🔍 Просмотреть свою активную подписку и её параметры
            - ❌ Отменить подписку (с выбором: немедленно или в конце периода)
            
            **Ключевые отличия от админ-эндпоинтов:**
            
            | Аспект | Админ-эндпоинты (`/api/v1/subscriptions`) | Студент-эндпоинты (`/api/v1/student`) |
            |--------|-------------------------------------------|---------------------------------------|
            | Авторизация | `X-Tenant-ID` + `X-API-Key` | `X-Student-Token` (JWT) |
            | Область данных | Все клиенты тенанта | Только данные текущего студента |
            | Действия | Создание, чтение, отмена для любого клиента | Только чтение/отмена своей подписки |
            | Безопасность | Проверка по API-ключу | Проверка по подписанному JWT |
            
            **Безопасность:**
            
            - Студент получает доступ **только к своим данным** — нельзя посмотреть подписки других студентов того же тенанта
            - Токен `X-Student-Token` содержит `tenantId` и `studentExternalId` в claims — дополнительная проверка не требуется
            - Все действия логируются в аудит-лог с указанием `actor: student:{id}`
            
            **Авторизация**
            
            Все запросы требуют заголовок:
            - `X-Student-Token` — JWT-токен, полученный после входа через `/api/v1/student/login`
            
            **Не требуются:** `X-Tenant-ID` и `X-API-Key` — они извлекаются из токена.
            """;

    public static final String GET_SUMMARY = "🔍 Получить свою активную подписку";

    public static final String GET_DESCRIPTION = """
            Возвращает информацию о текущей подписке студента.
            
            **Как определяется «активная» подписка:**
            
            Метод возвращает подписку по приоритету:
            1. 🔹 `active` — оплаченная активная подписка (наивысший приоритет)
            2. 🔸 `trialing` — пробный период (если нет активной)
            3. ⚪ Любая другая — если нет ни активной, ни триальной (для отображения истории)
            
            **Когда использовать:**
            
            - Отображение статуса подписки в личном кабинете студента
            - Показ даты следующего списания или окончания доступа
            - Проверка, может ли студент получить доступ к платному контенту
            
            **Что возвращается:**
            
            Ответ содержит те же поля, что и `SubscriptionResponse` из админ-эндпоинтов:
            - `id` — идентификатор подписки
            - `planId` / `planCode` — тарифный план
            - `amount`, `currency` — сумма и валюта
            - `status` — текущий статус (`active`, `trialing`, `canceled`, `expired`)
            - `currentPeriodEnd` — дата окончания текущего периода
            - `trialEndsAt` — дата окончания триала (если есть)
            
            **Пример использования во фронтенде:**
            
            ```javascript
            // Загрузка статуса подписки при входе в ЛК
            async function loadSubscriptionStatus() {
              const response = await fetch('/api/v1/student/my-subscription', {
                headers: { 'X-Student-Token': localStorage.getItem('student_token') }
              });
              
              if (response.status === 404) {
                showNoSubscriptionBanner(); // "У вас нет активной подписки"
                return;
              }
              
              const sub = await response.json();
              
              // Отображаем информацию
              document.getElementById('plan-name').textContent = sub.planName;
              document.getElementById('next-billing').textContent = 
                formatDate(sub.currentPeriodEnd);
              
              // Если есть триал — показываем таймер
              if (sub.status === 'trialing' && sub.trialEndsAt) {
                startTrialCountdown(sub.trialEndsAt);
              }
            }
            ```
            """;

    public static final String GET_SUCCESS_EXAMPLE = """
            {
              "id": "sub_9k8j7h6g5f4d3s2a",
              "tenantId": "6546tds",
              "customerId": "student_ivan_2024",
              "planId": "plan_premium_monthly",
              "planCode": "premium_monthly",
              "planName": "Премиум-доступ",
              "amount": 149000,
              "currency": "RUB",
              "status": "active",
              "trialEndsAt": null,
              "currentPeriodStart": "2026-03-01T00:00:00Z",
              "currentPeriodEnd": "2026-03-31T23:59:59Z",
              "createdAt": "2026-02-15T10:30:00Z",
              "updatedAt": "2026-03-01T00:00:01Z"
            }
            """;

    public static final String CANCEL_SUMMARY = "❌ Отменить свою подписку";

    public static final String CANCEL_DESCRIPTION = """
            Позволяет студенту отменить свою активную подписку.
            
            **Режимы отмены:**
            
            | Параметр `cancelImmediately` | Поведение |
            |------------------------------|-----------|
            | `false` (по умолчанию) | Подписка остаётся активной до конца оплаченного периода. Доступ сохраняется, авто-продление отключается. |
            | `true` | Подписка отменяется немедленно. Доступ к платному контенту закрывается сразу. Возврат средств не производится (настраивается). |
            
            **Когда использовать каждый режим:**
            
            🔹 **`cancelImmediately: false`** (рекомендуется по умолчанию)
            - Студент передумал продолжать подписку, но хочет допользоваться оплаченным периодом
            - Типичный сценарий: «Отменить авто-продление»
            
            🔹 **`cancelImmediately: true`**
            - Студент хочет немедленно прекратить доступ (например, при утечке аккаунта)
            - Требует явного подтверждения в интерфейсе: «Вы уверены? Доступ будет закрыт сразу»
            
            **Что происходит после отмены:**
            
            1. Статус подписки меняется на `canceled`
            2. Если `cancelImmediately = true` — доступ к контенту отзывается (через аудит-событие)
            3. Студент получает уведомление об отмене (если настроено)
            4. В аналитике фиксируется `churn` с причиной `customer_request`
            
            **Важно:**
            
            - Отмена **не возвращает деньги** — для возвратов используйте админ-эндпоинт или обратитесь в поддержку
            - После отмены студент может оформить новую подписку в любой момент
            - История подписки сохраняется для отчётности и аналитики
            
            **Пример сценария в интерфейсе:**
            
            ```
            [Личный кабинет] → [Моя подписка] → [Отменить подписку]
            
            ⚠️ Подтверждение отмены
            
            Вы отменяете подписку «Премиум-доступ».
            
            ○ Оставить доступ до 31 марта 2026 (рекомендуется)
            ● Закрыть доступ немедленно
            
            [Отменить подписку] [Назад]
            ```
            """;

    public static final String CANCEL_REQUEST_EXAMPLE = """
            {
              "cancelImmediately": false
            }
            """;

    public static final String CANCEL_SUCCESS_EXAMPLE = """
            {
              "message": "Subscription cancelled successfully"
            }
            """;

    public static final String CANCEL_ERROR_EXAMPLES = """
            // ❌ Нет активной подписки
            { "error": "No active subscription found" }
            
            // ❌ Неверный токен
            { "error": "Invalid token" }
            
            // ❌ Ошибка сервера
            { "error": "Internal server error" }
            """;
}
