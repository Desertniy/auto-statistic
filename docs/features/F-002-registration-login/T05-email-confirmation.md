# F-002/T05: Подтверждение email и повторная отправка письма

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-002/T04                        |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Пользователь подтверждает email по одноразовой ссылке из письма регистрации. Если письмо потерялось или ссылка
истекла, он запрашивает новое письмо. Требования: PRD FR-AUTH-3, FR-AUTH-4. Ограничение частоты повторной отправки
появится в F-003/T05.

## Критерии приёмки
- [ ] AC-1: Given неподтверждённый аккаунт и действующая ссылка из письма, When пользователь переходит по ней
  (веб-интерфейс передаёт токен в API), Then email подтверждён, ответ сообщает об успехе.
- [ ] AC-2: Given ссылка уже использована, When её применяют повторно, Then ошибка «ссылка уже использована
  или недействительна». Состояние аккаунта не меняется.
- [ ] AC-3: Given ссылке больше 24 часов, When её применяют, Then ошибка «срок действия ссылки истёк», email
  не подтверждён. Ссылка, применённая в пределах 24 часов, работает. В тестах время управляемо.
- [ ] AC-4: Given произвольный или искажённый токен, When его применяют, Then ошибка «ссылка недействительна».
- [ ] AC-5: Given неподтверждённый аккаунт, When запрошена повторная отправка для его email, Then приходит новое письмо,
  и после этого старая ссылка не работает (AC-2), а новая работает.
- [ ] AC-6: Given email без аккаунта или уже подтверждённый email, When запрошена повторная отправка, Then ответ
  такой же, как в AC-5, письмо подтверждения не отправляется. По ответу нельзя понять, есть ли аккаунт.
- [ ] AC-7: Given токен подтверждения, When просмотрены хранилище и логи, Then токен в открытом виде не хранится
  и не логируется (FR-PWD-6).

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
Решение — [architecture.md](./architecture.md) (API → `email-confirmation`, `email-confirmation/resend`, коды
`confirmation-link-*`, поток «Подтверждение email и повторная отправка», `EmailVerificationToken.consume`,
`ConfirmationError`, `ResendOutcome`), [ADR-0008](../../architecture/adr/ADR-0008-arrow-errors-as-values.md).
Таблица `email_verification_token` создана в T04 — новых миграций нет.

- **Модули / пакеты:**
  - `account.domain`: `EmailVerificationToken.consume(now)` и `ConfirmationError` (`Invalid`, `AlreadyUsedOrRevoked(userId)`,
    `Expired(userId)`), `ResendOutcome` (`Sent(userId)`, `UnknownEmail`, `AlreadyConfirmed(userId)`).
  - `account.application`:
    - `ConfirmEmail.confirm(rawToken: String, request: RequestContext): Either<ConfirmationError, UserId>` —
      `SecretToken.parse(raw) ?: Invalid`; `inTransaction { tokens.findByHashForUpdate(hash) ?: Invalid; token.consume(clock.instant());
      tokens.markUsed(id, now); users.confirmEmail(userId, now)` (`SET email_confirmed_at = COALESCE(email_confirmed_at, :now)`);
      `tokens.revokeActive(userId, now)` (кроме использованного) `}`.
    - `ResendConfirmation.resend(email: EmailAddress, request: RequestContext): ResendOutcome` — `inTransaction { users.findByEmail ⇒
      null ⇒ UnknownEmail; подтверждён ⇒ AlreadyConfirmed; иначе tokens.revokeActive(userId, now), tokens.insert(new) ⇒ Sent }`;
      после коммита только для `Sent` — `AccountEmails.confirmation(...)` (тот же текст, что в T04), сбой отправки ⇒ лог WARN.
      Результат — без `Either`: все исходы для клиента одинаковы.
    - `EmailVerificationTokenRepository` дополняется: `findByHashForUpdate(hash): EmailVerificationToken?`
      (`SELECT … FOR UPDATE`), `markUsed(id, at)`, `revokeActive(userId, at)` (`UPDATE … SET revoked_at = :at WHERE user_id = :u
      AND used_at IS NULL AND revoked_at IS NULL`); `UserRepository.confirmEmail(userId, at)`.
  - `account.api.AuthController`:
    - `POST /api/v1/auth/email-confirmation` — `ConfirmEmailRequest(token: String?)`; отсутствует/пустой ⇒ `400 validation-failed`
      (`field = token`, `REQUIRED`); `Invalid` ⇒ `400 confirmation-link-invalid`; `AlreadyUsedOrRevoked` ⇒ `400 confirmation-link-used`;
      `Expired` ⇒ `400 confirmation-link-expired`; `Right` ⇒ `200 {"message": "Email подтверждён. Теперь можно войти."}`.
      `userId` из ошибок наружу не отдаётся.
    - `POST /api/v1/auth/email-confirmation/resend` — `ResendConfirmationRequest(email: String?)`; ошибка `EmailAddress.parse` ⇒
      `400 validation-failed` (`field = email`); иначе всегда `202` с фиксированным `message` (architecture.md).
- **API-контракт:** таблицы API и кодов ProblemDetail в architecture.md.
- **Модель данных / миграции:** нет (T04, `V003`).
- **Срок ссылки:** `app.account.email-confirmation.token-ttl` (`PT24H`); действительна при `now < expiresAt`.
- **Тесты:**
  - Тестовая поддержка (вводится здесь): `support/MutableClock` (`Clock`, `fun set(instant)`, `fun advance(duration)`),
    `support/TestClockConfiguration` (`@TestConfiguration`, `@Bean @Primary fun clock(): MutableClock`). Каждая спецификация
    с `@Import(TestClockConfiguration::class)` выставляет время в начале теста.
  - unit `EmailVerificationTokenTest`: действующий ⇒ `usedAt = now`; `usedAt`/`revokedAt` заданы ⇒ `AlreadyUsedOrRevoked`;
    `now = createdAt + 23:59:59` ⇒ ок; `+ 24:00:00` ⇒ `Expired`; `+ 24:00:01` ⇒ `Expired`; использованный и истёкший ⇒
    `AlreadyUsedOrRevoked` (приоритет).
  - `EmailConfirmationApiTest` (`@SpringBootTest(RANDOM_PORT)`, `@Import(TestClockConfiguration)`, токен берётся из ссылки
    письма в `CapturedEmails`): AC-1 — `200`, `email_confirmed_at` заполнен; AC-2 — повтор ⇒ `confirmation-link-used`,
    `email_confirmed_at` не изменился; AC-3 — `advance(24h + 1s)` ⇒ `confirmation-link-expired`, email не подтверждён;
    `advance(23h59m)` ⇒ `200`; AC-4 — случайные 43 символа, `abc`, пустая строка (⇒ `validation-failed`), строка 10 000 символов ⇒
    `confirmation-link-invalid`; AC-5 — resend ⇒ новое письмо (`allFor` размер 2), старый токен ⇒ `confirmation-link-used`,
    новый ⇒ `200`; AC-6 — несуществующий и подтверждённый email ⇒ тот же статус и тело, что в AC-5, новых писем нет;
    AC-7 — в `email_verification_token.token_hash` нет значения токена (есть его SHA-256), `LogCapture` за весь сценарий не
    содержит токена.
- **Риски:** параллельное применение одной ссылки — `FOR UPDATE` сериализует, второй получает `AlreadyUsedOrRevoked`;
  тайминг resend различается для существующего/несуществующего email (ADR-0011, лимиты — F-003/T05); `MutableClock` живёт
  в кешированном Spring-контексте и общий для всех спецификаций с тем же `@Import` — поэтому каждый тест сам выставляет
  время в начале и не полагается на значение, оставленное другим тестом.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: AC-1…AC-7 покрыты, граница 24 ч проверена с обеих сторон (`23:59:59`, `24:00:00`, `24:00:01`),
  `FOR UPDATE` закрывает параллельное применение, ответы resend неразличимы. Миграций нет — корректно (таблица из T04).
- Замечания (обязательно учесть при реализации):
  - [major] `TestClockConfiguration` с `@Bean @Primary fun clock(): MutableClock` (так же в architecture.md, «Тестирование»)
    даёт бин с именем `clock` — то же имя, что у `ClockConfiguration.clock()` из T02. Переопределение бинов в Boot выключено ⇒
    `BeanDefinitionOverrideException`, контекст не стартует. → Назвать метод `mutableClock()` (`@Primary` оставить).
    Правило закреплено в `conventions.md` («Тесты»); architect поправит architecture.md при доработке T04.
  - [minor] `ResendConfirmation.resend` возвращает `ResendOutcome` без `Either`, а `TransactionRunner.inTransaction` принимает
    `() -> Either<E, A>`. → Использовать `Either<Nothing, ResendOutcome>` (`.right()` внутри, `.merge()` снаружи) либо
    добавить в `TransactionRunner` вариант без `Either`; не обходить `TransactionRunner` через `@Transactional`.
  - [minor] `MutableClock` живёт в кешированном контексте — хранить текущее время в `AtomicReference`/`@Volatile`
    (запросы обрабатываются потоками Tomcat, а время выставляет поток теста).
  - [minor] Два одновременных resend могут оставить два активных токена — допустимо (подтверждение отзывает остальные);
    дополнительной блокировки не нужно.

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
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: ConfirmEmail (FOR UPDATE, consume), ResendConfirmation (отзыв активных токенов), коды confirmation-link-*, MutableClock для управляемого времени; миграций нет |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY; обязательная правка при реализации — имя бина mutableClock (конфликт с бином clock); 3 minor |
