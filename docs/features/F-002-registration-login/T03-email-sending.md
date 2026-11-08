# F-002/T03: Отправка писем: конфигурация и dev-заглушка

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | F-002/T02                        |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Регистрации и безопасности аккаунта нужны письма: подтверждение, уведомления, восстановление пароля. Домен
и адрес отправителя пока неизвестны (PRD O1, отложено пользователем), почтовый сервис выбирает architect (QA4).
Поэтому задача даёт общий механизм отправки с параметрами из конфигурации и заглушку, которая в разработке и тестах
перехватывает письма. Содержание конкретных писем описано в задачах, где они появляются.

Ссылки в письмах ведут на веб-интерфейс, это отдельный проект (PRD Q2). Базовый адрес веб-интерфейса задаётся
конфигурацией (Допущение).

## Критерии приёмки
- [ ] AC-1: Given конфигурация, When просмотрены её параметры, Then адрес и имя отправителя, базовый адрес
  веб-интерфейса для ссылок и параметры почтового сервиса задаются через окружение. Секреты почтового сервиса
  в репозитории отсутствуют.
- [ ] AC-2: Given включён режим заглушки (по умолчанию в локальной разработке и тестах), When приложение отправляет
  письмо, Then письмо не уходит наружу, а сохраняется в заглушке: получатель, тема, текст. Тесты могут прочитать
  последнее письмо для адреса.
- [ ] AC-3: Given режим заглушки, When письмо отправлено, Then в лог пишется только факт отправки (получатель, тип
  письма), без содержимого и без ссылок с токенами (NFR-SEC-3).
- [ ] AC-4: Given почтовый сервис недоступен, When приложение отправляет письмо, Then ошибка записывается в лог,
  а пользовательская операция, вызвавшая письмо, не падает с внутренней ошибкой (Допущение). Пользователь может
  запросить письмо повторно там, где это предусмотрено.
- [ ] AC-5: Given эксплуатационный режим без настроенного адреса отправителя или адреса веб-интерфейса, When
  приложение запускается, Then старт завершается понятной ошибкой конфигурации. Молча отправлять письма с пустыми
  параметрами нельзя.
- [ ] AC-6: Given любое письмо, When оно сформировано, Then оно на русском языке, а ссылки в нём построены
  от настроенного адреса веб-интерфейса.

## Открытые вопросы
- (нет) Домен и отправитель — PRD O1, отложено пользователем. Почтовый сервис с обработкой данных в РФ — PRD O2 / QA4.

<!-- ============ architect ============ -->
## Технический план
Решение — [ADR-0011](../../architecture/adr/ADR-0011-email-smtp-stub.md), [architecture.md](./architecture.md) («Конфигурация»).
Почтовый сервис — любой SMTP-провайдер (выбор провайдера с обработкой в РФ, PRD O2, — параметры окружения, не код).
Конкретные письма (тексты) — в T04/T05; здесь только механизм.

- **Зависимости:** `org.springframework.boot:spring-boot-starter-mail` (без версии, BOM). Arrow уже есть (T02).
- **Модули / пакеты:**
  - `shared.domain.EmailAddress` — smart-конструктор по architecture.md («shared.domain»): нужен здесь как тип получателя,
    переиспользуется T04/T05 (unit-тест `EmailAddressTest`: trim + lowercase, 254/255 символов, форматы).
  - `mail.application`:
    - `EmailKind` (`@JvmInline value class EmailKind(val code: String)`), `EmailMessage(to: EmailAddress, kind: EmailKind, subject: String, body: String)`,
      `data object EmailDeliveryFailed`;
    - `fun interface EmailSender { fun send(message: EmailMessage): Either<EmailDeliveryFailed, Unit> }`.
  - `shared.infrastructure`:
    - `WebProperties` — `@ConfigurationProperties("app.web") data class(val baseUrl: URI?)`, в `init`: `requireNotNull(baseUrl)
      { "app.web.base-url (APP_WEB_BASE_URL) must be set: base URL of the web interface for links in emails" }` и
      `require(scheme in http/https && isAbsolute)`; обязателен во всех режимах (в `local`/`test` задан в профиле);
    - `WebLinks(webProperties)` — `fun link(path: String, query: Map<String, String> = emptyMap()): String` через
      `UriComponentsBuilder.fromUri(baseUrl).path(path)…encode()`; учитывает базовый URL с путём и без завершающего `/`.
  - `mail.infrastructure`:
    - `MailProperties` — `@ConfigurationProperties("app.mail") data class(val mode: MailMode = SMTP, val fromAddress: String?, val fromName: String = "auto-statistic")`,
      `enum MailMode { STUB, SMTP }`; в `init` при `SMTP`: `fromAddress` задан и проходит `EmailAddress.parse`, иначе
      `IllegalArgumentException("app.mail.from-address (APP_MAIL_FROM_ADDRESS) must be a valid email when app.mail.mode=smtp")`;
    - `MailConfiguration` — `@EnableConfigurationProperties(MailProperties, WebProperties)`; бин `EmailSender`:
      `@ConditionalOnProperty("app.mail.mode", havingValue = "stub")` ⇒ `StubEmailSender`;
      `havingValue = "smtp", matchIfMissing = true` ⇒ `SmtpEmailSender(mailSender.getIfAvailable() ?: error("app.mail.mode=smtp requires spring.mail.host (SPRING_MAIL_HOST)"), props)`
      (параметр `ObjectProvider<JavaMailSender>`);
    - `StubEmailSender : EmailSender, CapturedEmails` — `ConcurrentHashMap<EmailAddress, CopyOnWriteArrayList<EmailMessage>>`
      (изменяемое состояние — с комментарием-причиной «dev/test stub», ADR-0011); `CapturedEmails { fun lastFor(to: EmailAddress): EmailMessage?; fun allFor(to: EmailAddress): List<EmailMessage> }`;
      лог INFO `Email captured by stub: kind={}, to={}`;
    - `SmtpEmailSender` — `MimeMessageHelper(message, false, "UTF-8")`, `setFrom(InternetAddress(fromAddress, fromName, "UTF-8"))`,
      `setText(body, false)`; `catch (e: MailException)` ⇒ лог ERROR `Email delivery failed: kind={}, to={}, error={}` с
      `e.javaClass.simpleName` (без `e.message` и без стека с содержимым) ⇒ `EmailDeliveryFailed.left()`. Отдельная функция
      `buildMimeMessage(...)` для теста кодировки.
- **Конфигурация** (`application.yaml`):
  ```yaml
  app:
    mail:
      mode: smtp
      from-name: auto-statistic
    # app.mail.from-address и app.web.base-url — без умолчаний (APP_MAIL_FROM_ADDRESS, APP_WEB_BASE_URL)
  spring.mail:
    default-encoding: UTF-8
    properties:
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
      mail.smtp.starttls.enable: true
  management.health.mail.enabled: false
  ```
  `application-local.yaml` и `application-test.yaml`: `app.mail.mode: stub`, `app.web.base-url: http://localhost:3000`.
  Хост/порт/логин/пароль SMTP — только окружение `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
  `SPRING_MAIL_PASSWORD`. README: таблица переменных + необязательный Mailpit для локальной проверки в режиме `smtp`.
- **API-контракт / модель данных:** нет.
- **Доменные типы и ошибки:** `EmailAddress` + `EmailAddressError { Blank; TooLong; InvalidFormat }`; `EmailDeliveryFailed`.
- **Тесты:**
  - `StubEmailSenderTest` (unit, AC-2/AC-3): `lastFor`/`allFor`; лог содержит kind и адрес, **не** содержит тему, тело и
    маркер-«токен» из тела (`LogCapture`).
  - `SmtpEmailSenderTest` (unit, AC-4/AC-6): `JavaMailSenderImpl` на `127.0.0.1:<закрытый порт>` с таймаутами 1 с ⇒ `Left`,
    без исключения наружу, ERROR в логе без тела; `buildMimeMessage` ⇒ `Content-Type` содержит `charset=UTF-8`, `From`
    с именем, тема/тело на кириллице читаются без искажений.
  - `MailConfigurationTest` (AC-5, `ApplicationContextRunner` с `MailConfiguration` + `MailSenderAutoConfiguration`):
    `smtp` без `from-address` ⇒ контекст не стартует, сообщение причины содержит `app.mail.from-address`; без `base-url` ⇒
    `app.web.base-url`; без `spring.mail.host` ⇒ `spring.mail.host`; `stub` ⇒ бин `StubEmailSender`; всё задано ⇒ `SmtpEmailSender`.
  - `WebLinksTest` (AC-6): базовый URL со слешем/без, с путём, query кодируется (`+`, `=`, кириллица).
  - `MailConfigurationSecretsTest` (AC-1): основной `application.yaml` не содержит `spring.mail.password`/`username`/`host`.
- **Риски:** `WebProperties` обязателен во всех режимах — все тестовые контексты получают значение из профиля `test`
  (проверить `PortInUseTest`, `ServerPortOverrideTest`, `ApplicationContextRunner`-тесты); detekt `TooGenericExceptionCaught`
  не срабатывает — ловится только `MailException`.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: механизм отправки без конкретных писем — разумная граница; AC-1…AC-6 покрыты тестами, проверка
  конфигурации при старте и запрет логирования содержимого соответствуют ADR-0011. Зависимость от T02 (Arrow) обоснована.
- Замечания (не блокируют, учесть при реализации):
  - [minor] Имя `MailProperties` совпадает с классом Spring Boot (`…mail.autoconfigure.MailProperties`) — путаница в импортах
    и в `MailConfigurationTest`, где подключается `MailSenderAutoConfiguration`. → Назвать `AppMailProperties`.
  - [minor] `WebProperties`/`WebLinks` — общий код (`shared`), а включаются через `@EnableConfigurationProperties` в
    `mail.infrastructure.MailConfiguration`. → Вынести в `shared.infrastructure` (например, `WebConfiguration`), чтобы `account`
    в T04 не зависел от конфигурации `mail`.
  - [minor] `buildMimeMessage` выполняется вне `catch (MailException)`: `MessagingException`/`UnsupportedEncodingException` из
    `MimeMessageHelper`/`InternetAddress` вылетят наружу исключением. → Собирать сообщение внутри `MimeMessagePreparator`
    (Spring обернёт в `MailPreparationException`, подкласс `MailException`) или ловить и эти исключения точечно.
  - [minor] `StubEmailSenderTest`/`SmtpEmailSenderTest` помечены как unit, но используют `LogCapture` (корневой логгер) —
    допустимо; следить, чтобы они не шли параллельно с другими спецификациями, пишущими в тот же логгер.

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
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: SMTP через spring-boot-starter-mail, режимы stub/smtp, EmailSender → Either, WebLinks, проверка конфигурации при старте (ADR-0011); EmailAddress перенесён сюда из T04 как тип получателя; «Зависит от»: F-001 → F-002/T02 (нужны Arrow из T02 и тестовый профиль/support из T01) |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY, 4 minor (AppMailProperties, WebProperties в shared, MessagingException вне catch) |
