# Архитектура системы auto-statistic

> Документ ведёт агент `architect`. Обновляется при каждом изменении, затрагивающем систему целиком.

## Текущее состояние
Проект на стадии бутстрапа (фича F-001): переход с IntelliJ-модуля без системы сборки на Gradle-проект
с каркасом Spring Boot, health-check и проверками качества кода. Бизнес-функциональности и БД пока нет.
F-012 вводит версионирование: `version.properties` и `CHANGELOG.md` в корне репозитория (ADR-0006).
F-002 (в проектировании) вводит PostgreSQL, первую доменную логику и аутентификацию: контексты `legal`, `mail`,
`account`, `audit` и общий пакет `shared` (ADR-0007…ADR-0012, [architecture.md F-002](../features/F-002-registration-login/architecture.md)).

## Стек (зафиксирован ADR; изменения — только новым ADR)
| Область | Решение | ADR |
|---------|---------|-----|
| Платформа | Java 17 (LTS), Gradle toolchain `jvmToolchain(17)` | [ADR-0001](./adr/ADR-0001-jdk-17.md) |
| Язык | Kotlin 2.3.21 (версия, управляемая Spring Boot 4.1.2) | [ADR-0002](./adr/ADR-0002-gradle-build.md) |
| Сборка | Gradle 9.3.1 (Wrapper), Kotlin DSL, version catalog `gradle/libs.versions.toml`, один модуль, Spring Boot BOM через `platform(...)` | [ADR-0002](./adr/ADR-0002-gradle-build.md) |
| Приложение | Spring Boot 4.1.2, Web MVC (servlet, Tomcat), Jackson 3 | [ADR-0003](./adr/ADR-0003-spring-boot-web-mvc.md) |
| Health-check | Spring Boot Actuator, `GET /actuator/health`; по HTTP открыты только `health` и `info`, discovery `/actuator` выключен | [ADR-0003](./adr/ADR-0003-spring-boot-web-mvc.md), [ADR-0006](./adr/ADR-0006-app-versioning.md) |
| Версия приложения | `version.properties` (ключ `version`, SemVer `X.Y.Z`) → `project.version` (валидация в `build.gradle.kts`) → имя jar `auto-statistic-<версия>.jar` и `build-info` → `GET /actuator/info` (`build.name`, `build.version`); patch поднимается при слиянии задачи, журнал — `CHANGELOG.md` (`docs/WORKFLOW.md`, раздел 7) | [ADR-0006](./adr/ADR-0006-app-versioning.md) |
| Ошибки API | RFC 7807 `ProblemDetail` (`spring.mvc.problemdetails.enabled=true`) | [ADR-0003](./adr/ADR-0003-spring-boot-web-mvc.md) |
| Качество кода | ktlint 1.8.0 (`org.jlleitschuh.gradle.ktlint` 14.2.0, стиль `ktlint_official`) + detekt 2.0.0-alpha.3; оба ронят `build` | [ADR-0004](./adr/ADR-0004-code-quality-ktlint-detekt.md) |
| Тесты | Kotest 6.2.6 (`kotest-bom`): спецификации `FunSpec`, раннер `kotest-runner-junit6` на JUnit Platform 6.0.3 (только движок `kotest`), `kotest-assertions-core`; Spring — `kotest-extensions-spring` (`@ApplyExtension(SpringExtension::class)` на спецификацию) + Spring Boot Test, `RestTestClient`. JUnit Jupiter / AssertJ в тестах не используются (detekt `ForbiddenImport`) | [ADR-0005](./adr/ADR-0005-kotest-testing.md), [ADR-0003](./adr/ADR-0003-spring-boot-web-mvc.md) |

Решения F-002 (вступают в силу по мере реализации задач F-002, см. колонку «С задачи»):

| Область | Решение | ADR | С задачи |
|---------|---------|-----|----------|
| Хранилище | PostgreSQL 18; миграции Flyway (`db/migration`, `V<NNN>__*.sql`); доступ — Spring `JdbcClient`, SQL вручную в `…infrastructure.Jdbc*Repository`; UUID v4 из приложения; подключение `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`; health-компонент `db` | [ADR-0007](./adr/ADR-0007-postgresql-flyway-jdbcclient.md) | F-002/T01 |
| Тесты с БД | Testcontainers 2.x, `jdbc:tc:postgresql:18-alpine` в профиле `test` (включается в `tasks.test`), собственный контейнер для глобальных изменений; для сборки нужен Docker | [ADR-0007](./adr/ADR-0007-postgresql-flyway-jdbcclient.md) | F-002/T01 |
| Профили | без профиля — эксплуатационные значения без умолчаний для секретов; `local` (`compose.yaml`, почта-заглушка); `test` | [ADR-0007](./adr/ADR-0007-postgresql-flyway-jdbcclient.md), [ADR-0011](./adr/ADR-0011-email-smtp-stub.md) | F-002/T01 |
| Ошибки | Arrow Core 2.2.2.1 (`Either`, `Raise`, накопление); `TransactionRunner` (откат при `Left`), без `@Transactional` на `Either`; ProblemDetail проекта: `type urn:auto-statistic:problem:<code>`, расширения `code`, `errors[]` | [ADR-0008](./adr/ADR-0008-arrow-errors-as-values.md) | F-002/T02 |
| Аутентификация | Spring Security 7, закрыто по умолчанию; серверная сессия в PostgreSQL, непрозрачный токен в `Authorization: Bearer`, в БД — SHA-256; без cookie и CSRF | [ADR-0009](./adr/ADR-0009-auth-opaque-bearer-sessions.md) | F-002/T06 |
| Пароли | Argon2id (19 MiB, t=2, p=1) через `DelegatingPasswordEncoder` `{argon2id-v1}`, BouncyCastle; локальный список распространённых паролей (SecLists) | [ADR-0010](./adr/ADR-0010-password-hashing-argon2id.md) | F-002/T04 |
| Почта | SMTP (`spring-boot-starter-mail`), порт `EmailSender` → `Either`; `app.mail.mode: stub \| smtp`; отправка синхронно после коммита; ссылки от `app.web.base-url` | [ADR-0011](./adr/ADR-0011-email-smtp-stub.md) | F-002/T03 |
| Аудит | таблица `security_audit_event`, запись вне бизнес-транзакции (fail-open), хранение 1 год, очистка `@Scheduled` | [ADR-0012](./adr/ADR-0012-security-audit-log.md) | F-002/T07 |
| Время | бин `java.time.Clock` (UTC); бизнес-зона `Europe/Moscow`; в тестах — `MutableClock` | architecture.md F-002 | F-002/T02 |

Ещё **не решено** (решается ADR в первой фиче, где понадобится — не раньше):
- MockK (пока фейки портов), property-based-тесты (`kotest-property`), отчёты о покрытии.
- Ограничение частоты при нескольких экземплярах (F-003/T05), CORS и HTTPS/HSTS (F-011/T01), проверка зависимостей (F-011/T03).
- CI, Docker-образ, деплой.

## Координаты и структура пакетов
- `group = com.autostatistic`, `rootProject.name = auto-statistic`, базовый пакет `com.autostatistic`.
- Класс приложения `com.autostatistic.AutoStatisticApplication` — в корневом пакете (сканирование компонентов
  покрывает все подпакеты).
- Пакеты — **по фичам (bounded context), внутри — по слоям**:
  ```
  com.autostatistic
  ├── AutoStatisticApplication.kt
  ├── <context>/              ← например, trip
  │   ├── api/                ← контроллеры, DTO, маппинг Either → HTTP/ProblemDetail
  │   ├── application/        ← use cases, транзакции
  │   ├── domain/             ← чистые типы и функции, sealed-ошибки (без Spring)
  │   └── infrastructure/     ← репозитории, клиенты, Spring-конфигурация контекста
  └── shared/                 ← общие технические вещи (появляется только при реальной необходимости)
  ```
- Контексты (с F-002): `legal` (версии политики и согласия), `mail` (отправка писем), `account` (пользователь,
  регистрация, подтверждение email, сессии, Spring Security), `audit` (журнал безопасности); `shared` — `EmailAddress`,
  `RequestContext`, `SecretToken`, `TransactionRunner`, `ApiProblems`, `Clock`, `WebLinks`. Межконтекстные зависимости —
  только application → application (`account` → `legal`, `mail`, `audit`).
- API — префикс `/api/v1`; публичные пути перечислены в `SecurityConfiguration` (architecture.md F-002), всё остальное
  требует входа.
- Тесты зеркалируют пакеты main в `src/test/kotlin`.

## Слои
```
api (controllers, DTO, mapping)
  → application (use cases, оркестрация, транзакции)
    → domain (чистые функции, типы, доменные ошибки — без Spring)
  → infrastructure (репозитории, внешние клиенты, конфигурация)
```
Зависимости направлены внутрь: `domain` ни от чего не зависит. В одном Gradle-модуле границы слоёв
контролируются ревью (ADR-0002); автоматическая проверка (ArchUnit/Konsist) — при необходимости, отдельным решением.

## Сборка и проверки
| Команда | Что делает |
|---------|-----------|
| `./gradlew build` (Windows: `gradlew.bat build`) | компиляция, тесты, ktlint, detekt — эталонная проверка для ревью и QA |
| `./gradlew test` | только тесты |
| `java -jar build/libs/auto-statistic-<версия>.jar` | запуск собранного исполняемого jar (версия — из `version.properties`) |
| `./gradlew bootRun` | запуск приложения (порт 8080; переопределение: `--args='--server.port=9090'` или `SERVER_PORT`) |
| `docker compose up -d` + `./gradlew bootRun --args='--spring.profiles.active=local'` | локальный запуск с PostgreSQL из `compose.yaml` (с F-002/T01; без профиля нужны `DB_*` и прочие переменные окружения) |
| `./gradlew ktlintFormat` | автоисправление стиля |
| `./gradlew ktlintCheck detekt` | только проверки качества |

## Реестр ADR
| ADR | Название | Статус |
|-----|----------|--------|
| [ADR-0001](./adr/ADR-0001-jdk-17.md) | Платформа — JDK 17 (LTS) через Gradle toolchain | Accepted |
| [ADR-0002](./adr/ADR-0002-gradle-build.md) | Сборка — Gradle Kotlin DSL, Wrapper, version catalog, один модуль, BOM через platform | Accepted |
| [ADR-0003](./adr/ADR-0003-spring-boot-web-mvc.md) | Веб-стек — Spring Boot 4.1, Web MVC, Actuator, ProblemDetail | Accepted |
| [ADR-0004](./adr/ADR-0004-code-quality-ktlint-detekt.md) | Качество кода — ktlint + detekt, ронят `build` | Accepted |
| [ADR-0005](./adr/ADR-0005-kotest-testing.md) | Тесты — Kotest 6.2 (FunSpec, раннер JUnit 6, kotest-extensions-spring, Kotest-assertions) | Accepted |
| [ADR-0006](./adr/ADR-0006-app-versioning.md) | Версия приложения — `version.properties` → `project.version` → имя jar и `build-info` → `/actuator/info`; patch при слиянии задачи | Accepted |
| [ADR-0007](./adr/ADR-0007-postgresql-flyway-jdbcclient.md) | Хранилище — PostgreSQL 18, Flyway, Spring `JdbcClient`, Testcontainers | Accepted |
| [ADR-0008](./adr/ADR-0008-arrow-errors-as-values.md) | Ошибки как значения — Arrow `Either`/`Raise`, откат транзакций при `Left`, формат ошибок API | Accepted |
| [ADR-0009](./adr/ADR-0009-auth-opaque-bearer-sessions.md) | Аутентификация — Spring Security, серверные сессии в PostgreSQL, непрозрачный bearer-токен | Accepted |
| [ADR-0010](./adr/ADR-0010-password-hashing-argon2id.md) | Пароли — Argon2id через `DelegatingPasswordEncoder`, локальный список распространённых паролей | Accepted |
| [ADR-0011](./adr/ADR-0011-email-smtp-stub.md) | Письма — SMTP, режимы `stub`/`smtp`, синхронная отправка после коммита | Accepted |
| [ADR-0012](./adr/ADR-0012-security-audit-log.md) | Журнал аудита — таблица PostgreSQL, запись fail-open, хранение 1 год | Accepted |
