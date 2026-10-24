# Архитектура системы auto-statistic

> Документ ведёт агент `architect`. Обновляется при каждом изменении, затрагивающем систему целиком.

## Текущее состояние
Проект на стадии бутстрапа (фича F-001): переход с IntelliJ-модуля без системы сборки на Gradle-проект
с каркасом Spring Boot, health-check и проверками качества кода. Бизнес-функциональности и БД пока нет.
F-012 вводит версионирование: `version.properties` и `CHANGELOG.md` в корне репозитория (ADR-0006).

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

Ещё **не решено** (решается ADR в первой фиче, где понадобится — не раньше):
- PostgreSQL + Flyway + доступ к данным (Spring Data JDBC / jOOQ / Exposed), Testcontainers.
- Функциональная обработка ошибок: Arrow (`Either`, `Raise`) либо собственные `sealed`-результаты.
- MockK, property-based-тесты (`kotest-property`), отчёты о покрытии — подключаются вместе с первой доменной логикой.
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
