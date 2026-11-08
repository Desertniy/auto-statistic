# F-002/T07: Журнал аудита событий безопасности

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-002/T06                        |
| Оценка      | M                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Журнал аудита для расследования инцидентов (PRD NFR-SEC-3, FR-PD-7). Задача создаёт журнал и пишет в него события,
которые уже есть после T04–T06. Следующие фичи добавляют свои события, и это записано в их критериях приёмки.
API для просмотра журнала в MVP нет: журнал доступен оператору средствами эксплуатации (Допущение).

## Критерии приёмки
- [ ] AC-1: Given выполнены регистрация, попытка регистрации на занятый email, подтверждение email, повторная отправка
  письма, успешный вход, неуспешный вход (неверный пароль, неизвестный email, неподтверждённый email) и выход, When
  просмотрен журнал, Then на каждое действие есть запись с типом события и результатом.
- [ ] AC-2: Given любая запись, When она просмотрена, Then в ней есть время, пользователь (если известен), IP,
  User-Agent, тип события и результат. Для неуспешного входа с неизвестным email пользователь не указан, а введённый
  email записан (Допущение: это нужно для расследования перебора).
- [ ] AC-3: Given события из AC-1, When журнал проверен на секреты, Then в нём нет паролей, токенов подтверждения
  и учётных данных сессий.
- [ ] AC-4: Given приложение, When просмотрены его API, Then способа изменить или удалить записи журнала через API нет.
- [ ] AC-5: Given записи старше 1 года, When срабатывает очистка (по расписанию), Then эти записи удаляются или
  обезличиваются, а более новые остаются. В тестах время управляемо.
- [ ] AC-6: Given запись в журнал не удалась, When происходит событие, Then сбой журнала пишется в технический лог,
  а пользовательская операция не раскрывает внутреннюю ошибку (Допущение: решение, блокировать ли операцию при сбое
  аудита, принимает architect).

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
Решение — [ADR-0012](../../architecture/adr/ADR-0012-security-audit-log.md) (хранение, fail-open — ответ на вопрос AC-6:
**операция не блокируется** при сбое аудита, хранение 1 год удалением), [architecture.md](./architecture.md)
(`security_audit_event`, `audit.domain`, поток «Аудит»). Сигнатуры use cases T04–T06 уже возвращают исходы с нужными
данными (`RegistrationOutcome`, `ConfirmationError` с `userId`, `ResendOutcome`, `LoginFailure`) и принимают
`RequestContext` — задача добавляет вызовы записи, не меняя контрактов.

- **Модули / пакеты:**
  - `audit.domain`: `SecurityEventType`, `SecurityEventOutcome`, `SecurityAuditEvent(id, occurredAt, type, outcome, userId: UserId?,
    subjectEmail: String?, request: RequestContext)`; фабрики в companion — по одной на строку таблицы ниже
    (`registered(userId, ctx, at)`, `loginUnknownEmail(enteredEmail, ctx, at)` и т.д.); `subjectEmail` обрезается до 254.
    Полей для пароля/токена/ссылки нет.
  - `audit.application`:
    - `SecurityAuditLog` — `fun record(event: SecurityAuditEvent)`: `try { repository.insert(event) } catch (e: Exception) { log.error(…) }`
      — точечный `@Suppress("TooGenericExceptionCaught")` с причиной «fail-open, ADR-0012»; лог: `Security audit write failed:
      type={}, outcome={}, error={}` (`e.javaClass.simpleName`).
    - `SecurityAuditRetention.purgeExpired(now: Instant): Int` — `repository.deleteOlderThan(now - retention)`, лог INFO с числом строк.
    - порт `SecurityAuditRepository { insert(event); deleteOlderThan(threshold): Int }`.
  - `audit.infrastructure`: `JdbcSecurityAuditRepository`; `AuditProperties` (`@ConfigurationProperties("app.audit")`:
    `retention: Duration = P365D`, `purgeCron: String = "0 30 3 * * *"`); `SecurityAuditRetentionJob` —
    `@Scheduled(cron = "\${app.audit.purge-cron}", zone = "Europe/Moscow")` ⇒ `purgeExpired(clock.instant())`;
    `AuditSchedulingConfiguration` — `@EnableScheduling` (первое использование планировщика в проекте).
  - Вызовы `SecurityAuditLog.record` (после завершения транзакции use case, `occurredAt = clock.instant()`):

    | Use case (задача) | Исход | `event_type` / `outcome` | `user_id` | `subject_email` |
    |-------------------|-------|--------------------------|-----------|-----------------|
    | `RegisterUser` (T04) | `Registered` | `REGISTRATION` / `SUCCESS` | новый | — |
    | `RegisterUser` | `EmailAlreadyRegistered` | `REGISTRATION` / `EMAIL_ALREADY_REGISTERED` | существующий | — |
    | `ConfirmEmail` (T05) | `Right` | `EMAIL_CONFIRMATION` / `SUCCESS` | да | — |
    | `ConfirmEmail` | `Invalid` | `EMAIL_CONFIRMATION` / `INVALID_TOKEN` | — | — |
    | `ConfirmEmail` | `AlreadyUsedOrRevoked` / `Expired` | `EMAIL_CONFIRMATION` / `TOKEN_ALREADY_USED` / `TOKEN_EXPIRED` | да | — |
    | `ResendConfirmation` (T05) | `Sent` / `AlreadyConfirmed` / `UnknownEmail` | `EMAIL_CONFIRMATION_RESEND` / `SENT` / `SKIPPED_ALREADY_CONFIRMED` / `SKIPPED_UNKNOWN_EMAIL` | да / да / — | — / — / введённый email |
    | `Login` (T06) | `Right` | `LOGIN` / `SUCCESS` | да | — |
    | `Login` | `WrongPassword` / `EmailNotConfirmed` | `LOGIN` / `INVALID_PASSWORD` / `EMAIL_NOT_CONFIRMED` | да | — |
    | `Login` | `UnknownEmail` | `LOGIN` / `UNKNOWN_EMAIL` | — | введённый email (trim, до 254) |
    | `Logout` (T06) | — | `LOGOUT` / `SUCCESS` | да | — |

    Ошибки валидации (`400 validation-failed`) и запросы без входа (`401`) не аудируются (не события безопасности в смысле
    NFR-SEC-3 для F-002; отказ в доступе к чужому объекту — F-004).
- **API-контракт:** нового API нет; контроллеров для журнала не создаётся (AC-4).
- **Модель данных / миграции:** `V005__security_audit_event.sql` — таблица по ADR-0012 / architecture.md, индекс
  `security_audit_event_occurred_at_idx (occurred_at)`, функция + триггер `security_audit_event_no_update`
  (`BEFORE UPDATE … RAISE EXCEPTION 'security_audit_event is append-only'`).
- **Конфигурация:** `application.yaml` — `app.audit.retention: P365D`, `app.audit.purge-cron: "0 30 3 * * *"`.
- **Тесты:**
  - unit: фабрики `SecurityAuditEvent` (обрезка `subjectEmail`, сочетания тип/исход); `SecurityAuditLogTest` — фейк-репозиторий
    бросает исключение ⇒ `record` не бросает, в `LogCapture` ERROR с типом и исходом.
  - `SecurityAuditFlowTest` (`@SpringBootTest(RANDOM_PORT)`, уникальные email, выборка записей по `user_id`/`subject_email`
    своих пользователей): AC-1 — все действия из AC-1 ⇒ по записи на каждое с ожидаемыми `event_type`/`outcome`;
    AC-2 — у каждой записи `occurred_at`, `ip` (`127.0.0.1`), `user_agent` (заданный в запросе заголовок), для
    `UNKNOWN_EMAIL` — `user_id IS NULL` и `subject_email` = введённый; AC-3 — ни одна колонка ни одной записи не содержит
    пароль, токен подтверждения (из письма) и токен сессии.
  - AC-4: `GET/POST/PUT/PATCH/DELETE` на `/api/v1/audit…`, `/api/v1/security-audit…` с входом ⇒ `404`/`405`; тест
    `RequestMappingHandlerMapping` — ни один маппинг не содержит `audit` в пути; прямой `UPDATE` ⇒ исключение триггера.
  - `SecurityAuditRetentionTest` (собственный контейнер — глобальное удаление): вставить записи с `occurred_at` = `now − 366d`,
    `now − 365d − 1s`, `now − 364d`, вызвать `purgeExpired(now)` ⇒ остаётся только последняя; запись ровно на границе
    `now − 365d` остаётся (удаляются строго старше).
  - AC-6: `@SpringBootTest` с `@MockitoBean SecurityAuditRepository`, бросающим `DataAccessResourceFailureException` ⇒ вход
    возвращает `200` (успех операции), выход — `204`, в `LogCapture` ERROR `Security audit write failed`.
  - Дополнить `RegistrationLoginFlowTest` проверкой событий сквозного сценария (AC-5 фичи).
- **Риски:** дополнительный `INSERT` на каждый вход — несущественно; потеря событий при сбое БД аудита (fail-open) —
  осознанно; `@EnableScheduling` включает планировщик для всего приложения — cron ежедневный, в тестах не мешает;
  права БД «только INSERT» — F-011.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: таблица «исход → событие» покрывает все действия AC-1, fail-open (ответ на AC-6) обоснован ADR-0012,
  секретов в модели нет по построению, граница очистки (`строго старше`) проверена. Контракты T04–T06 не меняются.
- Замечания (учесть при реализации):
  - [minor] Строка `RegisterUser`/`EmailAlreadyRegistered` зависит от решения по ветке гонки в T04 (`existingUserId` может быть
    неизвестен) — после доработки T04 сверить таблицу (`user_id` или `subject_email`).
  - [minor] AC-6 через `@MockitoBean SecurityAuditRepository`: в Kotlin `Mockito.any()` для non-null параметра `insert(event)`
    даёт NPE при стабе. → `@TestConfiguration` с `@Bean @Primary` бросающим фейком (имя бина не совпадает с основным), см.
    `conventions.md` «Тесты».
  - [minor] AC-5 говорит «по расписанию», а тесты вызывают только `purgeExpired`. → Добавить проверку подключения задачи:
    `ScheduledTaskHolder.scheduledTasks` содержит cron-задачу с выражением из `app.audit.purge-cron`.
  - [minor] Зона в `@Scheduled(zone = "Europe/Moscow")` — литерал, дублирующий `BusinessTime.ZONE`. → `const val ZONE_ID` в
    `BusinessTime` и ссылаться на него из аннотации и из `ZoneId.of(...)`.

<!-- ============ kotlin-backend-developer ============ -->
## Реализация
- **Изменённые файлы:** ...
- **Что сделано:** ...
- **Тесты:** какие добавлены, как запустить
- **Отклонения от плана:** (и почему)

<!-- ============ tech-lead ============ -->
## Ревью кода
- Вердикт: — (IN_TESTING / CHANGES_REQUESTED)
- Замечания (`[blocker]` / `[major]` / `[minor]`, файл:строка):

<!-- ============ qa-engineer ============ -->
## Тестирование
| Кейс | Проверяет AC | Тип (auto/manual) | Результат |
|------|--------------|-------------------|-----------|

- Вердикт: — (DONE / CHANGES_REQUESTED)
- Найденные дефекты (шаги, ожидаемо, фактически):

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-24 | product-manager | — → DRAFT | Задача создана при декомпозиции MVP (PRD, раздел 12) |
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: security_audit_event (V005), запись вне транзакции fail-open (ответ на вопрос AC-6: операцию не блокировать), очистка по cron через 1 год (ADR-0012), таблица «исход → событие» |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY, 4 minor (зависимость от решения T04 по гонке, фейк вместо @MockitoBean, проверка расписания) |
