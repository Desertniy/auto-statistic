# F-002/T06: Вход, выход и защита API

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-002/T05                        |
| Оценка      | M                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Пользователь с подтверждённым email входит по email и паролю и получает сессию. С ней он обращается к защищённым
ресурсам. Первый такой ресурс — данные своего аккаунта («кто я»). Выход завершает сессию на сервере. Все ресурсы,
кроме явно публичных, требуют входа. Требования: PRD FR-AUTH-5..8, FR-SES-3. Механизм сессий выбирает architect (QA1).
Сроки жизни сессий и выход со всех устройств — F-003.

## Критерии приёмки
- [ ] AC-1: Given подтверждённый аккаунт, When вход с верными email (в любом регистре) и паролем, Then сессия открыта,
  и запрос «кто я» с ней возвращает email и имя пользователя, без пароля и служебных данных.
- [ ] AC-2: Given неверный пароль для существующего email и любой пароль для несуществующего email, When вход,
  Then в обоих случаях одна и та же ошибка «неверный email или пароль». Ответы неразличимы по телу и коду,
  а время ответа заметно не различается (оба пути выполняют проверку пароля).
- [ ] AC-3: Given неподтверждённый аккаунт и верный пароль, When вход, Then вход отклонён с сообщением «подтвердите
  email» и подсказкой о повторной отправке письма. С неверным паролем ответ как в AC-2 (Допущение: так факт
  существования аккаунта не раскрывается).
- [ ] AC-4: Given запрос к защищённому ресурсу без учётных данных сессии или с произвольными, When он отправлен,
  Then ответ «не аутентифицирован» в едином формате ошибок.
- [ ] AC-5: Given публичные ресурсы (регистрация, подтверждение и повторная отправка, вход, документы политики
  и согласия, проверка работоспособности), When они вызываются без входа, Then они доступны. Любой другой
  существующий ресурс без входа недоступен (AC-4).
- [ ] AC-6: Given пользователь вошёл на двух устройствах, When выходит на первом, Then учётные данные первой сессии
  сразу перестают приниматься, хотя срок их жизни не истёк, а вторая сессия продолжает работать.
- [ ] AC-7: Given учётные данные сессии, When просмотрены ответы и логи, Then учётные данные не передаются в URL
  и не пишутся в логи.

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
Решение — [ADR-0009](../../architecture/adr/ADR-0009-auth-opaque-bearer-sessions.md) (механизм сессий, ответ на PRD QA1),
[ADR-0010](../../architecture/adr/ADR-0010-password-hashing-argon2id.md) (выравнивание времени),
[architecture.md](./architecture.md) (API → login/logout/me, публичные пути, коды `invalid-credentials`,
`email-not-confirmed`, `unauthenticated`, поток «Вход, запрос с токеном, выход», `LoginFailure`).

- **Зависимости:** `org.springframework.boot:spring-boot-starter-security` (без версии, BOM).
- **Модули / пакеты:**
  - `account.domain`: `SessionId`, `AuthSession`, `LoginFailure` (`UnknownEmail(enteredEmail)`, `WrongPassword(userId)`,
    `EmailNotConfirmed(userId)`), `AuthenticatedUser(userId: UserId, sessionId: SessionId)`.
  - `account.application`:
    - `Login.login(rawEmail: String, password: RawPassword, request: RequestContext): Either<LoginFailure, IssuedSession>` —
      `EmailAddress.parse(rawEmail)` ⇒ ошибка ⇒ `hasher.matchesDummy(password)` и `UnknownEmail(rawEmail.trim())`;
      `users.findByEmail` ⇒ `null` ⇒ `matchesDummy` + `UnknownEmail`; `!hasher.matches` ⇒ `WrongPassword`;
      `!isEmailConfirmed` ⇒ `EmailNotConfirmed`; иначе `token = tokenGenerator.generate()`, `sessions.insert(AuthSession(…, token.hash(), now))` ⇒
      `IssuedSession(sessionId, userId, token)`. Проверка пароля выполняется **ровно один раз** в каждой ветке.
    - `Logout.logout(principal: AuthenticatedUser, request: RequestContext)` — `sessions.revoke(sessionId, now)`
      (`UPDATE … SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL`), идемпотентно.
    - `AuthenticateToken.authenticate(raw: String): AuthenticatedUser?` — `SecretToken.parse` ⇒ `sessions.findActiveByHash(hash)`
      (`SELECT id, user_id FROM auth_session WHERE token_hash = :h AND revoked_at IS NULL`).
    - `GetCurrentAccount.get(userId): CurrentAccount(email, displayName)`.
    - Порт `AuthSessionRepository { insert; findActiveByHash; revoke }`.
  - `account.infrastructure.security`:
    - `SecurityConfiguration` — один `SecurityFilterChain` по ADR-0009, п. 1: `csrf/httpBasic/formLogin/logout/requestCache`
      выключены, `STATELESS`; `authorizeHttpRequests`: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`,
      публичные пути из architecture.md (любой метод) `permitAll()`, `anyRequest().authenticated()`;
      `exceptionHandling { authenticationEntryPoint(ProblemDetailAuthenticationEntryPoint) }`;
      `addFilterBefore(BearerSessionAuthenticationFilter(authenticateToken), AnonymousAuthenticationFilter::class.java)`.
      Фильтр создаётся **внутри** конфигурации, а не как `@Component`/`@Bean` — иначе Boot зарегистрирует его ещё и как
      сервлет-фильтр, и он выполнится дважды.
    - `BearerSessionAuthenticationFilter : OncePerRequestFilter` — заголовок `Authorization` начинается с `Bearer ` (регистр
      схемы не важен) ⇒ `authenticateToken` ⇒ найдено ⇒ `SessionAuthenticationToken(principal)` (наследник
      `AbstractAuthenticationToken`, `authenticated = true`, без authorities, `credentials = null`) в `SecurityContextHolder`;
      иначе — без контекста. Никогда не логирует заголовок/токен.
    - `ProblemDetailAuthenticationEntryPoint` — `401`, `Content-Type: application/problem+json`, `WWW-Authenticate: Bearer`, тело
      `ApiProblems.problem(UNAUTHORIZED, "unauthenticated", …)` сериализуется бином `JsonMapper` (Jackson 3).
  - `account.api`:
    - `AuthController.login(LoginRequest(email: String?, password: String?), ctx)` — `null`/пустые ⇒ `400 validation-failed`
      (`REQUIRED`); `UnknownEmail`, `WrongPassword` ⇒ `401 invalid-credentials` (одинаковые тело и заголовки);
      `EmailNotConfirmed` ⇒ `403 email-not-confirmed` + свойство `resendConfirmationPath`; `Right` ⇒
      `200 LoginResponse(accessToken, tokenType = "Bearer")`.
    - `AuthController.logout(@AuthenticationPrincipal principal: AuthenticatedUser, ctx)` ⇒ `204`.
    - `AccountController.me(@AuthenticationPrincipal principal)` ⇒ `GET /api/v1/account/me` ⇒ `200 {"email", "name"}`.
- **API-контракт:** architecture.md, раздел «API». Токен — только в теле ответа на вход и в заголовке `Authorization`,
  никогда в URL или query.
- **Модель данных / миграции:** `V004__auth_session.sql` — `auth_session (id uuid PK, user_id uuid NOT NULL REFERENCES app_user,
  token_hash varchar(64) NOT NULL UNIQUE, created_at timestamptz NOT NULL, revoked_at timestamptz NULL)`.
- **Изменения тестов F-001:** `HealthEndpointTest` — «unknown path returns 404» ⇒ без входа `401 unauthenticated`
  (`application/problem+json`) и `404` с валидным токеном; проверки `405` на `POST /actuator/health` и `404` для
  `/actuator/env` остаются (пути actuator `health`/`info` публичны для любых методов; `/actuator/env` без входа даёт `401` —
  тест переписать на запрос с токеном или ожидать `401`). Если к моменту реализации слита F-012/T02 — `InfoEndpointTest`
  должен остаться зелёным без изменений.
- **Тесты:**
  - unit `LoginTest` (фейки): каждая ветка вызывает проверку пароля ровно один раз (счётчик в фейке `PasswordHasher`),
    неподтверждённый + неверный пароль ⇒ `WrongPassword`.
  - `LoginApiTest` (`@SpringBootTest(RANDOM_PORT)`; пользователи создаются через API T04 + подтверждение T05 или прямой
    `UPDATE email_confirmed_at` в подготовке): AC-1 — вход с email в другом регистре, `me` ⇒ `email`, `name`, нет других полей;
    AC-2 — неверный пароль и неизвестный email ⇒ одинаковые `status`, `Content-Type` и тело целиком (путь один, поэтому
    и `instance` совпадает); время: медианы 5 попыток каждой ветки отличаются не более чем в 2 раза (обе ветки выполняют
    Argon2); AC-3 — неподтверждённый + верный ⇒ `403 email-not-confirmed` с `resendConfirmationPath`; + неверный ⇒ как AC-2.
  - `ApiProtectionTest`: AC-4 — `me`/`logout` без заголовка, с `Bearer xyz`, с `Basic …`, с отозванным токеном ⇒ `401`
    ProblemDetail `unauthenticated`; AC-5 — каждый публичный путь без входа не даёт `401` (health `200`, legal `200`,
    register/confirm/resend/login с пустым телом ⇒ `400`, не `401`), а `GET /api/v1/does-not-exist` ⇒ `401`; публичный путь
    со «старым» токеном работает.
  - `LogoutApiTest`: AC-6 — две сессии (два входа), выход первой ⇒ её токен `401` на `me`, второй ⇒ `200`; повторный
    `logout` со старым токеном ⇒ `401`.
  - AC-7: `LogCapture` на весь сценарий вход → `me` → выход не содержит токен; в `auth_session` хранится не токен, а его SHA-256.
  - Сквозной `account/api/RegistrationLoginFlowTest` (AC-1/AC-4 фичи): регистрация → письмо → подтверждение → вход → `me` →
    выход → `me` = `401`; без согласия — регистрация отклонена; без подтверждения — вход `403`.
- **Риски:** глобальное изменение поведения — все будущие эндпоинты закрыты по умолчанию (это и нужно); тест времени AC-2
  может быть нестабилен на медленном CI — порог «в 2 раза» и медианы, а не единичные замеры; CORS не настраивается (F-011/T01),
  поэтому веб-клиент с другого домена до F-011 не сможет вызвать API из браузера — для F-002 не требуется.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: AC-1…AC-7 покрыты; выравнивание времени проверено и детерминированно (счётчик в фейке), и по HTTP
  (медианы, порог ×2); фильтр не регистрируется дважды; изменения тестов F-001 описаны. Соответствует ADR-0009/0010.
- Замечания (обязательно учесть при реализации):
  - [major] Не покрыт AC-6 фичи («после перезапуска аккаунт и согласие на месте»): T01 проверяет перезапуск только на
    таблице-пробе, T02 — на документах. → Добавить `account/api/RestartPersistenceTest` (собственный контейнер, `AppRunner`):
    регистрация → подтверждение (токен из `CapturedEmails` первого запуска) → закрыть контекст → второй запуск → вход `200`,
    `me` отдаёт email/имя, строка `user_consent` пользователя на месте.
  - [major] При подключении `spring-boot-starter-security` автоконфигурация `UserDetailsServiceAutoConfiguration` (наличие
    `SecurityFilterChain` её не отключает) создаёт in-memory пользователя и пишет в лог WARN `Using generated security
    password: …` — лишний способ входа и учётные данные в логе (противоречит AC-7 по духу). → Исключить эту автоконфигурацию
    (`spring.autoconfigure.exclude` или `@SpringBootApplication(exclude = …)`) и в AC-7-тесте проверить, что в логе старта
    нет `generated security password`.
  - [minor] `ProblemDetailAuthenticationEntryPoint` пишет ответ вручную — `instance` (путь запроса) нужно выставить самому
    (вне контроллера Spring MVC этого не сделает); `ApiProtectionTest` должен проверять тот же набор полей, что и для ошибок
    контроллеров.
  - [minor] `GetCurrentAccount.get(userId)` возвращает не-null: пользователь сессии обязан существовать (FK), без `!!` —
    `checkNotNull`/исключение инфраструктуры.
  - [minor] `BearerSessionAuthenticationFilter` — класть аутентификацию через `SecurityContextHolder.getContextHolderStrategy()`
    (`createEmptyContext()` + `setContext`), как рекомендует Spring Security 6+/7, а не мутировать общий контекст.

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
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: Spring Security, непрозрачный bearer-токен серверной сессии в auth_session (V004, ADR-0009), выравнивание времени входа, правки тестов F-001 (401 вместо 404 без входа) |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY; обязательные дополнения при реализации — тест перезапуска (AC-6 фичи), исключение UserDetailsServiceAutoConfiguration; 3 minor |
