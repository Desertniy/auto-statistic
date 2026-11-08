# F-002/T04: Регистрация с согласием и проверкой пароля

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-002/T01, F-002/T02, F-002/T03  |
| Оценка      | M                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Пользователь регистрируется по email и паролю, указывает имя (по желанию) и даёт согласие на обработку ПДн
с принятием политики конфиденциальности. Аккаунт создаётся неподтверждённым, на email уходит письмо со ссылкой
подтверждения: сама ссылка обрабатывается в T05. Требования: PRD FR-AUTH-1..3, FR-PWD-1, FR-PWD-2, FR-PWD-6,
FR-PD-2, FR-PD-4.

## Критерии приёмки
- [ ] AC-1: Given корректные данные (email, пароль от 12 символов, согласие и принятие политики с идентификаторами
  текущих версий из T02), When отправлен запрос регистрации, Then создан аккаунт в состоянии «email не подтверждён»,
  а в заглушке почты появилось письмо со ссылкой подтверждения. Ответ не содержит пароля и ссылки.
- [ ] AC-2: Given успешная регистрация, When просмотрены сохранённые данные, Then хранится факт согласия:
  пользователь, дата и время, версии согласия и политики, IP. Пароль хранится только в виде результата адаптивной
  функции хеширования с солью, в открытом виде его нет ни в хранилище, ни в логах.
- [ ] AC-3 (email): Given email `  User@Example.COM ` с пробелами и в разном регистре, When регистрация, Then email
  сохранён как `user@example.com`. Given email некорректного формата или длиннее 254 символов, When регистрация,
  Then ошибка с указанием поля.
- [ ] AC-4 (пароль): Given пароль длиной 11 символов, When регистрация, Then ошибка «не короче 12 символов».
  Пароль ровно из 12 символов принимается. Длиннее 128 — ошибка. Пароль с пробелами и кириллицей из 12+ символов
  принимается. Пароль из списка распространённых или скомпрометированных (например, `password1234`) — ошибка
  с объяснением. Пароль, совпадающий с email, — ошибка.
- [ ] AC-5 (согласие): Given в запросе нет согласия или оно `false`, When регистрация, Then ошибка, аккаунт не создан.
  Given указана нетекущая или несуществующая версия политики или согласия, When регистрация, Then ошибка «примите
  актуальную версию», аккаунт не создан.
- [ ] AC-6 (имя): Given имя длиннее 100 символов, When регистрация, Then ошибка с указанием поля. Без имени
  регистрация проходит.
- [ ] AC-7 (занятый email): Given уже есть аккаунт с `user@example.com`, When кто-то регистрируется с `USER@example.com`,
  Then ответ такой же, как при успешной регистрации, второй аккаунт не создан, пароль существующего не изменён,
  а владельцу уходит письмо «кто-то пытался зарегистрироваться на ваш email».

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
Решение — [architecture.md](./architecture.md) (API → `POST /api/v1/auth/register`, коды ошибок полей, «Модель данных»,
`account.domain`, поток «Регистрация»), [ADR-0010](../../architecture/adr/ADR-0010-password-hashing-argon2id.md) (пароли),
[ADR-0008](../../architecture/adr/ADR-0008-arrow-errors-as-values.md) (накопление ошибок, `TransactionRunner`, формат ошибок),
[ADR-0007](../../architecture/adr/ADR-0007-postgresql-flyway-jdbcclient.md), п. 3 (занятый уникальный ключ — `INSERT … ON CONFLICT`),
[ADR-0011](../../architecture/adr/ADR-0011-email-smtp-stub.md) (письмо после коммита).

- **Зависимости:** `org.springframework.security:spring-security-crypto` (без версии, BOM; стартер security — только в T06),
  `org.bouncycastle:bcprov-jdk18on` (версия `bouncycastle = "1.86"` в каталоге).
- **Модули / пакеты:**
  - `shared.domain`: `IpAddress`, `UserAgent`, `RequestContext`, `SecretToken` (+ `hash(): TokenHash`, `parse`), `TokenHash` — по architecture.md.
  - `shared.application`: `TransactionRunner` (ADR-0008, п. 3), порт `fun interface SecretTokenGenerator { fun generate(): SecretToken }`.
  - `shared.infrastructure`: `TransactionConfiguration` (бин `TransactionRunner` поверх `TransactionTemplate` с уровнем изоляции
    по умолчанию — `READ COMMITTED` PostgreSQL; не повышать, см. «Риски»), `SecureRandomTokenGenerator` (32 байта `SecureRandom`,
    `Base64.getUrlEncoder().withoutPadding()`).
  - `shared.api`: `RequestContextArgumentResolver` (`HandlerMethodArgumentResolver` для параметра `RequestContext`:
    `remoteAddr`, заголовок `User-Agent` обрезан до 512), `SharedWebMvcConfiguration` (`WebMvcConfigurer.addArgumentResolvers`),
    `ApiProblems.validation(errors: NonEmptyList<FieldError>)` (`400`, `code = validation-failed`, свойство `errors`),
    `FieldError(field: String, code: String, message: String)`.
  - `account.domain`: `UserId`, `DisplayName`, `RawPassword`, `ValidPassword`, `PasswordHash`, `PasswordPolicy`,
    `PasswordViolation`, `CommonPasswords`, порт `PasswordHasher` (`hash`, `matches`, `matchesDummy` — ADR-0010, п. 3), `User`,
    `Consent`, `EmailVerificationToken` (`consume` реализуется в T05), `RegistrationOutcome` — по architecture.md.
    Константы: `MIN = 12`, `MAX = 128` code points.
    - `PasswordPolicy.validate(raw: RawPassword, email: EmailAddress?, common: CommonPasswords): Either<PasswordViolation, ValidPassword>` —
      при `email == null` (email в запросе отсутствует или невалиден) правило `EqualsEmail` пропускается, длина и список
      проверяются как обычно.
  - `account.application`:
    - `RegistrationCommand(email: EmailAddress, password: ValidPassword, displayName: DisplayName?, privacyPolicyVersion: LegalDocumentVersionId,
      consentVersion: LegalDocumentVersionId, request: RequestContext)` — согласие и принятие политики уже проверены (`true`) при разборе;
    - `sealed interface RegistrationError { data class OutdatedVersions(val fields: NonEmptyList<OutdatedField>) }`
      (`OutdatedField { PRIVACY_POLICY_VERSION, CONSENT_VERSION }`);
    - `RegisterUser.register(command): Either<RegistrationError, RegistrationOutcome>` — шаги:
      1. `LegalDocumentQueries.current()`, сравнить id ⇒ `Left(OutdatedVersions)` (до хеширования и записи);
      2. `hasher.hash(password)` — всегда, до транзакции (выравнивание работы для нового и занятого email, ADR-0010 п. 4);
      3. `transactions.inTransaction { … }` возвращает `Either<Nothing, RegistrationEffect>` — внутренний
         `private sealed interface RegistrationEffect { NewAccount(userId, email, token: SecretToken); ExistingAccount(existingUserId, email) }`:
         - `users.insert(user)` (вставка **без** предварительного `findByEmail`, см. порт ниже);
         - `Left(EmailTaken)` ⇒ `users.findByEmail(email)` в **той же** транзакции (она не сломана — ошибки БД не было; при
           `READ COMMITTED` новый оператор видит строку, закоммиченную конкурентной регистрацией) ⇒ `ExistingAccount(existing.id)`;
           `null` — нарушение инварианта (в F-002 пользователи не удаляются) ⇒ `error("app_user row for conflicting email not found")`
           (исключение ⇒ откат ⇒ `500`);
         - `Right` ⇒ `consents.insert(…)`, `token = tokenGenerator.generate()`, `tokens.insert(EmailVerificationToken(…, tokenHash =
           token.hash(), expiresAt = now + ttl))` ⇒ `NewAccount`.

         Занятый email обрабатывается **одним** путём — и при давно существующем аккаунте, и при гонке двух одновременных
         регистраций; `DuplicateKeyException` в этом потоке не возникает.
      4. После коммита — письмо: `NewAccount` ⇒ `AccountEmails.confirmation(to, link)`, `ExistingAccount` ⇒
         `AccountEmails.registrationAttempt(to)`; `Left` отправки ⇒ лог WARN `Registration email not delivered: kind=…` и продолжить
         (ERROR с деталями пишет сам `SmtpEmailSender`, T03);
      5. Результат: `Registered(userId)` / `EmailAlreadyRegistered(existingUserId)` — `existingUserId` известен всегда (нужен T07,
         строка таблицы аудита `user_id` = существующий не меняется).
    - порты:
      - `UserRepository { fun findByEmail(email: EmailAddress): User?; fun insert(user: User): Either<EmailTaken, Unit> }`
        (`data object EmailTaken`). Контракт `insert`: занятый email ⇒ `Left(EmailTaken)` **без исключения и без порчи текущей
        транзакции**; остальные методы — T05;
      - `ConsentRepository { insert }`, `EmailVerificationTokenRepository { insert }` (остальные методы — T05);
    - `AccountEmails` — тексты на русском: подтверждение (тема «Подтвердите email в auto-statistic», тело со ссылкой
      `webLinks.link(linkPath, mapOf("token" to token.value))` и сроком «24 часа»), попытка регистрации (тема «Попытка
      регистрации с вашим email», тело: «Кто-то пытался зарегистрироваться в auto-statistic с этим адресом. Если это были вы —
      просто войдите. Если нет — ничего делать не нужно, ваш аккаунт в безопасности.»). `EmailKind`: `email-confirmation`,
      `registration-attempt`.
    - `AccountProperties` — `@ConfigurationProperties("app.account")`: `emailConfirmation.tokenTtl: Duration = PT24H`,
      `emailConfirmation.linkPath: String = "/confirm-email"`.
  - `account.infrastructure`: `Argon2PasswordHasher` (`DelegatingPasswordEncoder("argon2id-v1", mapOf("argon2id-v1" to
    Argon2PasswordEncoder(16, 32, 1, 19456, 2)))`; при создании вычисляет `dummyHash` случайного пароля для `matchesDummy`
    — используется в T06), `CommonPasswordsResource` (читает `classpath:security/common-passwords.txt` при старте, пропускает
    пустые строки и `#`-комментарии, `lowercase(Locale.ROOT)` в `Set`), `JdbcUserRepository`, `JdbcConsentRepository`,
    `JdbcEmailVerificationTokenRepository`.
    - `JdbcUserRepository.insert`: `INSERT INTO app_user (id, email, display_name, password_hash, email_confirmed_at, created_at)
      VALUES (…) ON CONFLICT (email) DO NOTHING`; `update()` вернул `0` ⇒ `Left(EmailTaken)`, `1` ⇒ `Right(Unit)`.
      `DuplicateKeyException` **не перехватывается** (конфликт `id` при UUID v4 — практически невозможен и даёт `500`).
      Цель конфликта — именно `(email)`: прочие нарушения ограничений (`CHECK`, PK) остаются исключениями.
  - `account.api`: `AuthController.register(@RequestBody RegisterRequest, ctx: RequestContext)` → `RegisterRequestParser`
    (`zipOrAccumulate` по полям, все ошибки сразу) → `RegisterUser` → `202 RegistrationAcceptedResponse(message)`;
    `OutdatedVersions` ⇒ `400 validation-failed` с `NOT_CURRENT_VERSION` по соответствующим полям. Правила разбора:
    - пароль: `null`/пустая строка ⇒ `REQUIRED`; иначе `PasswordPolicy.validate(raw, parsedEmailOrNull, common)` — пароль
      проверяется **всегда**, email передаётся, только если он валиден (иначе `null`, `EQUALS_EMAIL` неприменимо);
    - `privacyPolicyVersionId` / `consentVersionId`: `null` или `isBlank()` ⇒ `REQUIRED`; иначе `LegalDocumentVersionId.parse(raw)`
      (без trim) ⇒ `Left(InvalidVersionId)` ⇒ `NOT_CURRENT_VERSION` (синтаксически неверный id — тоже «не текущая версия»);
      существующий, но нетекущий или несуществующий id ⇒ `OutdatedVersions` ⇒ `NOT_CURRENT_VERSION`.
  - Ресурс `src/main/resources/security/common-passwords.txt` — по ADR-0010, п. 6 (источник/коммит SecLists в заголовке,
    фильтр 12–128, обязательно `password1234`).
- **API-контракт:** `POST /api/v1/auth/register`, тело `RegisterRequest`, ответ `202` с фиксированным `message`, ошибки —
  таблица кодов полей в architecture.md. Ответ не содержит пароля, токена, ссылки, id пользователя.
- **Модель данных / миграции:** `V003__app_user_consent_verification.sql` — `app_user`, `user_consent`,
  `email_verification_token` с ограничениями и индексами по architecture.md (уникальное ограничение `app_user_email_uk` на
  `email` обязательно — на нём держится `ON CONFLICT (email)`).
- **Тесты:**
  - unit: `PasswordPolicyTest` (11 → `TooShort`, 12 → ок, 128 → ок, 129 → `TooLong`, кириллица + пробелы 12+ → ок,
    `password1234` и `PASSWORD1234` → `Common`, пароль = email в другом регистре → `EqualsEmail`, `email = null` и пароль,
    похожий на email (`user@example.com1`), → ок, `email = null` и 11 символов → `TooShort`, длина в code points:
    12 эмодзи = 12 символов), `DisplayNameTest` (100/101, пробелы ⇒ отсутствует), `SecretTokenTest`, `RegisterRequestParserTest`
    (несколько ошибок сразу, `false`/отсутствие согласия; невалидный email + пароль из 11 символов ⇒ обе ошибки `email` и
    `password/TOO_SHORT`; версия `""` и `"   "` ⇒ `REQUIRED`; версия `"Bad Id!"` ⇒ `NOT_CURRENT_VERSION`), `RegisterUserTest`
    с фейками портов (порядок: проверка версий до хеширования и записи; хеширование выполняется и для занятого email; фейк
    `insert` ⇒ `Left(EmailTaken)` ⇒ `EmailAlreadyRegistered(id из findByEmail)`, consent/token не пишутся, письмо
    `registration-attempt`; письмо не отправляется при `Left(OutdatedVersions)`).
  - `Argon2PasswordHasherTest`: хеш начинается с `{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$`, `matches` верно/неверно, два
    хеша одного пароля различаются (соль), `matchesDummy(any)` выполняется без исключения (возвращает `Unit`).
  - `JdbcUserRepositoryTest` (`@SpringBootTest`, общий контейнер): `insert` нового ⇒ `Right`, `findByEmail` возвращает его;
    **внутри одного `transactions.inTransaction`** — `insert` пользователя с занятым email ⇒ `Left(EmailTaken)`, затем в той же
    транзакции `findByEmail` возвращает существующего и транзакция успешно коммитится (без исключения; это и есть проверка
    «транзакция не сломана»); `CHECK` на lowercase (прямой `INSERT` email в верхнем регистре ⇒ исключение).
  - `RegisterUserIntegrationTest` (`@SpringBootTest`, настоящие репозитории и `TransactionRunner`, `CapturedEmails`):
    - занятый email: заранее зарегистрировать `e`, вызвать `register` с `e` ⇒ `Right(EmailAlreadyRegistered(existingUserId =
      id первого))`, исключения нет, в `app_user` одна строка с `e` и прежним `password_hash`, нового `user_consent`/
      `email_verification_token` нет, в `CapturedEmails` письмо `registration-attempt` на `e`;
    - гонка: два потока (`CountDownLatch` для одновременного старта) вызывают `register` с одним новым email ⇒ оба `Right`,
      ровно один `Registered` и один `EmailAlreadyRegistered` с тем же `userId`, одна строка в `app_user`, одно письмо
      `email-confirmation` и одно `registration-attempt`, ни одного исключения. (Ветка конфликта — единственная для
      занятого email, поэтому её детерминированно покрывает и первый сценарий, и AC-7 API-теста; потоковый тест проверяет
      поведение PostgreSQL `ON CONFLICT` при конкурентной вставке.)
  - `RegistrationApiTest` (`@SpringBootTest(RANDOM_PORT)`, общий контейнер, уникальные email): AC-1 — `202`, строка `app_user`
    с `email_confirmed_at IS NULL`, письмо в `CapturedEmails` со ссылкой `http://localhost:3000/confirm-email?token=…`, тело
    ответа без пароля/ссылки; AC-2 — `user_consent` (user, `given_at`, обе версии, `ip`), `password_hash` не содержит пароль,
    `LogCapture` не содержит пароль; AC-3 — `  User@Example.COM ` ⇒ `user@example.com`, неверный формат и 255 символов ⇒
    `errors[].field = email`; AC-4 — все граничные пароли; AC-5 — без/`false` согласия, без принятия политики, нетекущая,
    несуществующая и синтаксически неверная версия ⇒ `400` (`NOT_CURRENT_VERSION`), строк в `app_user` для email нет;
    AC-6 — 101 символ ⇒ `field = name`, без имени ⇒ `202`; AC-7 — повтор с `USER@…` ⇒ тот же статус и тело, в `app_user` одна
    строка, `password_hash` не изменился, владельцу письмо `registration-attempt`.
- **Декомпозиция:** задача не разбивается (замечание tech-lead о верхней границе M — по усмотрению). Причина: выделение
  «политика паролей + хешер + список» дало бы задачу без проверяемого продуктового результата (AC-2/AC-4 проверяются только
  через регистрацию), а последовательность T04 → T05 → T06 и так строгая. Рекомендуемый порядок внутри задачи для
  разработчика: shared-типы и `TransactionRunner` → домен пароля + `Argon2PasswordHasher` + ресурс (unit-тесты) → миграция
  и репозитории (интеграционные тесты) → `RegisterUser` → парсер и контроллер.
- **Риски:**
  - гонка двух регистраций одного email — закрыта `INSERT … ON CONFLICT (email) DO NOTHING` + чтение существующего в той же
    транзакции (ADR-0007, п. 3): транзакция не переходит в aborted, ответ `202`, письмо владельцу уходит;
  - корректность чтения после конфликта опирается на `READ COMMITTED`: при `REPEATABLE READ`/`SERIALIZABLE` PostgreSQL
    ответит ошибкой сериализации — изоляцию `TransactionRunner` не повышать (зафиксировано в ADR-0007, п. 3);
  - будущее удаление аккаунтов (не F-002): удаление между конфликтом и чтением даст `500` по инварианту — пересмотреть
    при появлении удаления;
  - Argon2 ~19 MiB/вызов — в тестах параллельно не нагружать (потоковый тест — ровно 2 потока); тайминг-различие веток
    (ADR-0011) принимается; размер ресурса паролей — проверить, что jar не раздувается сверх ~1–2 МБ.

<!-- ============ tech-lead ============ -->
## Ревью плана
### Круг 2 (повторное ревью)
- Вердикт: **READY**
- Все замечания круга 1 закрыты:
  - гонка/aborted-транзакция — `INSERT … ON CONFLICT (email) DO NOTHING` + `findByEmail` в той же транзакции; ошибки БД нет,
    транзакция коммитится, `DuplicateKeyException` в потоке не возникает. Семантика PostgreSQL верна: конкурентная вставка ждёт
    коммита «победителя», получает `0` строк, а следующий оператор при `READ COMMITTED` видит закоммиченную строку; при откате
    «победителя» вставка проходит. Ограничение по изоляции зафиксировано в ADR-0007 п. 3 и в «Рисках»;
  - `existingUserId` известен всегда — строка аудита T07 (`EMAIL_ALREADY_REGISTERED`, `user_id` = существующий) не меняется;
  - тесты: транзакция после конфликта коммитится (`JdbcUserRepositoryTest`), занятый email и два параллельных потока
    (`RegisterUserIntegrationTest`), AC-7 через API; ветка занятого email единственная, поэтому покрыта детерминированно;
  - `PasswordPolicy.validate(…, email: EmailAddress?, …)` — синхронизировано с architecture.md, тесты на `email = null`;
  - синтаксически неверный id версии ⇒ `NOT_CURRENT_VERSION`, пустой/из пробелов ⇒ `REQUIRED` — в таблице кодов и тестах парсера;
  - `matchesDummy` — в ADR-0010 п. 3; `mutableClock()` — в architecture.md; отказ от разбиения задачи обоснован.
- Замечания (не блокируют, выполнить при реализации):
  - [minor] В «Тестах» перечислен только `JdbcUserRepositoryTest`, а conventions («База данных») требуют интеграционный тест
    для **каждого** репозитория. → Добавить `JdbcConsentRepositoryTest` и `JdbcEmailVerificationTokenRepositoryTest` (вставка +
    чтение своей строки прямым SQL: `inet` через `host(ip)`, `timestamptz`, `token_hash`), не полагаясь только на `RegistrationApiTest`.
  - [minor] Потоковый тест: ждать завершения потоков с таймаутом (`ExecutorService` + `Future.get(timeout)`) и пробрасывать
    исключения из потоков в тест, а не терять их в фоне — иначе «ни одного исключения» фактически не проверяется.
- conventions.md («База данных») синхронизирован с ADR-0007 п. 3: `ON CONFLICT` — основной путь, `DuplicateKeyException` — запасной.

### Круг 1
- Вердикт: **NEEDS_REWORK** → architect (технические замечания; продуктовые AC не меняются)
- Декомпозиция и AC: продуктовая часть в порядке — AC-1…AC-7 проверяемы, покрывают граничные (11/12/128/129, кириллица,
  регистр email) и негативные случаи, занятый email не раскрывается. Возврат — из-за ошибки в транзакционной логике плана.
- Замечания:
  - [major] Ветка гонки «`DuplicateKeyException` ⇒ `EmailAlreadyRegistered`» описана **внутри** `inTransaction`:
    `users.insert ⇒ Left(EmailTaken) ⇒ EmailAlreadyRegistered` (т.е. `Right`). В PostgreSQL ошибка уникальности переводит
    транзакцию в состояние aborted; возврат `Right` приведёт к попытке коммита, pgjdbc ответит исключением («database returned
    ROLLBACK…») → `500` вместо `202`, и письмо владельцу не уйдёт. Это ещё и противоречит ADR-0008 п. 3 (`Left` ⇒ откат).
    → Описать в плане: `EmailTaken` выходит из `inTransaction` как `Left` (откат), снаружи маппится в
    `Right(EmailAlreadyRegistered)` + письмо «попытка регистрации». Альтернатива — `INSERT … ON CONFLICT (email) DO NOTHING`
    с проверкой числа строк (транзакция не ломается). Дописать соответствующее уточнение в ADR-0007 п. 3 (правило уже
    закреплено в `conventions.md`, раздел «База данных»).
  - [major] Из того же пункта: в ветке гонки `existingUserId` для `RegistrationOutcome.EmailAlreadyRegistered(existingUserId)`
    неизвестен (вставка не удалась), а он нужен аудиту T07. → Указать способ: повторный `findByEmail` после отката (вне
    транзакции) либо сделать поле nullable и описать, что пишет аудит в этом случае (согласовать с таблицей T07).
  - [major] Нет теста на ветку гонки — единственная ветка, где возможен `500`. → Добавить интеграционный тест `RegisterUser`
    с настоящим `JdbcUserRepository` и обёрткой, у которой `findByEmail` возвращает `null` для заранее вставленного email:
    ожидается `Right(EmailAlreadyRegistered)`, одна строка в `app_user`, письмо `registration-attempt`, нет исключения.
  - [minor] Противоречие сигнатуры и правила разбора: `PasswordPolicy.validate(raw, email: EmailAddress, common)` требует
    валидный email, а `RegisterRequestParser` при невалидном email должен проверить длину и список. → Сделать параметр
    `email: EmailAddress?` (правило `EqualsEmail` пропускается при `null`) и отразить в architecture.md («account.domain»).
  - [minor] Не указан код ошибки для синтаксически неверного `privacyPolicyVersionId`/`consentVersionId` (не проходит
    `LegalDocumentVersionId.parse`). → `NOT_CURRENT_VERSION` (пустая строка — `REQUIRED`), добавить в таблицу кодов и в
    `RegisterRequestParserTest`.
  - [minor] Порт `PasswordHasher.matchesDummy` есть в architecture.md, но не в ADR-0010 п. 3. → Синхронизировать текст ADR.
  - [minor] Задача на верхней границе M (shared-типы, `TransactionRunner`, резолвер `RequestContext`, домен пароля, Argon2,
    ресурс паролей, 3 репозитория, use case, парсер, контроллер, миграция). Разбиение (например, «политика паролей + хешер +
    список» отдельной задачей) — на усмотрение architect, не обязательно.
  - [minor] Попутно (не T04): в architecture.md, раздел «Тестирование», `@Bean @Primary fun clock(): MutableClock` конфликтует
    по имени с бином `clock` из T02 — заменить на `mutableClock()` (см. «Ревью плана» T05).

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
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: app_user/user_consent/email_verification_token (V003), PasswordPolicy + Argon2id (ADR-0010), TransactionRunner, накопление ошибок полей и формат ProblemDetail (ADR-0008), письма после коммита |
| 2026-11-08 | tech-lead | PLAN_REVIEW → NEEDS_REWORK | Ревью плана: NEEDS_REWORK → architect: гонка DuplicateKeyException внутри транзакции (aborted tx в PostgreSQL → 500), existingUserId в ветке гонки, нет теста гонки; 5 minor |
| 2026-11-08 | architect | NEEDS_REWORK → PLAN_REVIEW | Доработка плана по ревью: занятый email — `INSERT … ON CONFLICT (email) DO NOTHING` + `findByEmail` в той же транзакции (единый путь для занятого email и гонки, транзакция не ломается, `existingUserId` известен всегда, READ COMMITTED); тесты транзакции после конфликта и двух параллельных регистраций; `PasswordPolicy.validate(…, email: EmailAddress?, …)`; синтаксически неверный id версии ⇒ `NOT_CURRENT_VERSION`; ADR-0007 п. 3 и ADR-0010 п. 3 уточнены, architecture.md синхронизирован (`mutableClock()`); задача не разбивается (обоснование в плане) |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Повторное ревью плана: READY, замечания круга 1 закрыты (ON CONFLICT + чтение в той же транзакции при READ COMMITTED, тест гонки, `email?` в PasswordPolicy, коды версий); 2 minor (интеграционные тесты consent/token репозиториев, сбор исключений потоков); conventions.md «База данных» синхронизирован с ADR-0007 п. 3 |
