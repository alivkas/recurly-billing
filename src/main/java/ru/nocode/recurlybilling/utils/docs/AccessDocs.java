package ru.nocode.recurlybilling.utils.docs;

public final class AccessDocs {

    public static final String TAG_DESCRIPTION = """
            Проверка прав доступа студента к платному контенту.
            
            **Зачем нужен этот эндпоинт?**
            
            `AccessController` отвечает на вопрос: **«Может ли этот студент смотреть этот курс?»**
            
            Типичный сценарий использования:
            ```
            Студент открывает урок в LMS
                     ↓
            LMS вызывает: GET /api/v1/access/check?studentId=...&planCode=...
                     ↓
            Recurly проверяет: есть ли активная подписка на этот план?
                     ↓
            Ответ: { "hasAccess": true, "expiresAt": "2026-04-30" }
                     ↓
            LMS: показывает контент ИЛИ предлагает оформить подписку
            ```
            
            **Ключевые особенности:**
            
            - ✅ **Быстрый ответ**: метод оптимизирован для частых вызовов (кэширование, индексы)
            - ✅ **Идемпотентность**: повторный запрос с теми же параметрами вернёт тот же результат
            - ✅ **Безопасность**: проверка принадлежности студента к тенанту предотвращает доступ к чужим данным
            - ✅ **Детальные статусы**: коды `HAS_ACCESS`, `NO_ACTIVE_ACCESS`, `STUDENT_NOT_FOUND` помогают фронтенду показать правильное сообщение
            
            **Что проверяется:**
            
            1. Валидность заголовков авторизации (`X-Tenant-ID`, `X-API-Key`)
            2. Принадлежность студента (`studentId`) к указанному тенанту
            3. Наличие активной подписки на план с указанным `planCode`
            4. Срок действия доступа (если есть `expiresAt`)
            
            **Авторизация**
            
            Все запросы требуют заголовки:
            - `X-Tenant-ID` — идентификатор вашей организации
            - `X-API-Key` — секретный ключ
            """;

    public static final String CHECK_SUMMARY = "🔐 Проверить доступ студента к плану";

    public static final String CHECK_DESCRIPTION = """
            Проверяет, имеет ли студент активный доступ к указанному тарифному плану.
            
            **Параметры запроса:**
            
            | Параметр | Где | Описание | Пример |
            |----------|-----|----------|--------|
            | `studentId` | Query | Внешний идентификатор студента (из вашей LMS) | `student_ivan_2024` |
            | `planCode` | Query | Код тарифного плана | `premium_monthly` |
            | `X-Tenant-ID` | Header | ID вашей организации | `6546tds` |
            | `X-API-Key` | Header | Секретный ключ | `sk_live_***` |
            
            **Возможные ответы:**
            
            | Статус | `hasAccess` | `status` | Когда возвращается |
            |--------|-------------|----------|-------------------|
            | ✅ Доступ есть | `true` | `HAS_ACCESS` | Подписка активна, срок не истёк |
            | ❌ Нет доступа | `false` | `NO_ACTIVE_ACCESS` | Подписки нет, или она отменена/истекла |
            | ❌ Студент не найден | `false` | `STUDENT_NOT_FOUND` | `studentId` не зарегистрирован в этом тенанте |
            | ❌ Ошибка авторизации | — | — | Неверный `X-API-Key` или `X-Tenant-ID` (HTTP 401) |
            
            **Примеры использования:**
            
            🔹 **В фронтенде (показ/блокировка контента):**
            ```javascript
            async function canAccessContent(studentId, planCode) {
              const response = await fetch(
                `/api/v1/access/check?studentId=${studentId}&planCode=${planCode}`,
                { headers: { 'X-Tenant-ID': tenantId, 'X-API-Key': apiKey } }
              );
              const data = await response.json();
              
              if (data.hasAccess) {
                showCourseContent();
                showExpiryBadge(data.expiresAt); // "Доступ до 30 апреля"
              } else if (data.status === 'NO_ACTIVE_ACCESS') {
                showUpgradePrompt(planCode); // "Оформите подписку для доступа"
              } else {
                showError('Студент не найден');
              }
            }
            ```
            
            🔹 **В LMS (проверка перед загрузкой урока):**
            ```java
            // Псевдокод интеграции с Moodle/GetCourse
            if (!accessService.checkAccess(studentExternalId, coursePlanCode)) {
                redirect('/upgrade?plan=' + coursePlanCode);
            } else {
                loadLessonContent();
            }
            ```
            
            **Производительность:**
            
            - Метод оптимизирован для высоких нагрузок (сотни проверок в секунду)
            - Результаты кэшируются на 60 секунд (настраивается)
            - Для массовых проверок (например, при импорте студентов) используйте пакетный эндпоинт (планируется)
            
            **Безопасность:**
            
            - Проверка `isStudentBelongsToTenant` гарантирует, что нельзя проверить доступ студента из другого тенанта, даже зная его `studentId`
            - В логах не фиксируются полные идентификаторы студентов
            """;

    public static final String CHECK_SUCCESS_EXAMPLE = """
            {
              "hasAccess": true,
              "expiresAt": "2026-04-30",
              "status": "HAS_ACCESS",
              "reason": null
            }
            """;

    public static final String CHECK_NO_ACCESS_EXAMPLE = """
            {
              "hasAccess": false,
              "expiresAt": null,
              "status": "NO_ACTIVE_ACCESS",
              "reason": "Subscription canceled by user"
            }
            """;

    public static final String CHECK_NOT_FOUND_EXAMPLE = """
            {
              "hasAccess": false,
              "expiresAt": null,
              "status": "STUDENT_NOT_FOUND",
              "reason": "Student with ID 'student_unknown' not found in this tenant"
            }
            """;
}

