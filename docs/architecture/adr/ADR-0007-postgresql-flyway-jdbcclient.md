# ADR-0007: Хранилище — PostgreSQL 18, Flyway, доступ через Spring `JdbcClient`, тесты на Testcontainers
- Статус: Accepted
- Дата: 2026-11-08
- Фича: F-002

## Контекст
F-002 — первая фича с данными (PRD NFR-3): аккаунты, согласия, токены, сессии, журнал аудита. Целевой стек
(overview) называет PostgreSQL, Flyway и Testcontainers, но способ доступа к данным (Spring Data JDBC / jOOQ /
Exposed / JDBC) не выбран. Требования, которые влияют на выбор:
- доменный слой — неизменяемые `data class` и `@JvmInline value class` без аннотаций персистентности (conventions);
- запросы простые (поиск по уникальному ключу, вставка, пометка «использован/отозван»), но с нюансами PostgreSQL:
  `inet`, `bytea`, `timestamptz`, `INSERT … ON CONFLICT`, `SELECT … FOR UPDATE`;
- ПДн хранятся в РФ (FR-PD-1) — это требование к размещению, на выбор технологии не влияет;
- несколько экземпляров приложения (NFR-6) — состояние только в БД.

Проверено по Spring Boot 4.1.2 «Dependency Coordinates» (2026-11-08): BOM управляет `org.postgresql:postgresql` 42.7.14,
`org.flywaydb:flyway-core` и `flyway-database-postgresql` 12.4.0, `org.testcontainers:testcontainers` и
`testcontainers-postgresql` 2.0.5 (Testcontainers 2.x: класс `org.testcontainers.postgresql.PostgreSQLContainer`,
модуль зависит от `testcontainers-jdbc`). В Boot 4 есть стартеры `spring-boot-starter-jdbc`, `spring-boot-starter-flyway`,
`spring-boot-starter-jdbc-test`, `spring-boot-starter-flyway-test` и модуль `spring-boot-testcontainers`.

## Решение
1. **СУБД — PostgreSQL 18.** Локально и в тестах — образ `postgres:18-alpine` (одна мажорная версия с эксплуатацией).
   Требование размещать эксплуатационную БД и её резервные копии в РФ фиксируется в `README.md` (FR-PD-1).
2. **Миграции — Flyway** через `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` (без версий, BOM).
   Каталог `src/main/resources/db/migration`, имена `V<NNN>__<snake_case>.sql` (трёхзначный номер, conventions).
   Миграция выполняется при старте приложения; применённые миграции не редактируются.
3. **Доступ к данным — `org.springframework.jdbc.core.simple.JdbcClient`** (`spring-boot-starter-jdbc`), SQL пишется
   руками в классах `…infrastructure.Jdbc*Repository`, маппинг строк — явными функциями в доменные типы.
   ORM / Spring Data репозитории / jOOQ не используются.
   - Только именованные параметры (`:name`), никакой конкатенации значений в SQL (NFR-SEC-5).
   - Время: параметры `timestamptz` передаются как `OffsetDateTime` в UTC (`OffsetDateTime.ofInstant(i, ZoneOffset.UTC)`),
     читаются `rs.getObject(col, OffsetDateTime::class.java).toInstant()`; в домене — `Instant`.
   - `inet`: запись `CAST(:ip AS inet)`, чтение `host(ip)`; `bytea` ↔ `ByteArray`.
   - Нарушение уникального ключа, которое является бизнес-исходом (занятый email), превращается в репозитории в
     `Either.Left`. В PostgreSQL любая ошибка оператора переводит транзакцию в состояние aborted: дальнейшие операторы
     отклоняются, а коммит заканчивается исключением. Поэтому:
     - **по умолчанию** — `INSERT … ON CONFLICT (<колонки ключа>) DO NOTHING` и проверка числа вставленных строк
       (`0` ⇒ `Left`). Ошибки нет, транзакция остаётся рабочей: после `Left` в ней можно продолжать (например, прочитать
       существующую строку) и коммитить. Цель конфликта указывается явно — остальные нарушения ограничений остаются
       исключениями. Так сделаны `JdbcUserRepository.insert` (F-002/T04) и синхронизация версий документов (F-002/T02);
     - перехват `org.springframework.dao.DuplicateKeyException` допустим, только если полученный `Left` **выходит** из
       `TransactionRunner.inTransaction` (откат, ADR-0008 п. 3). Превращать его в `Right`, выполнять после него операторы
       или коммитить ту же транзакцию запрещено;
     - чтение «победившей» строки после `ON CONFLICT DO NOTHING` в той же транзакции корректно при уровне изоляции
       `READ COMMITTED` (умолчание PostgreSQL и `TransactionRunner`): каждый оператор видит строки, закоммиченные
       конкурентной транзакцией. При `REPEATABLE READ`/`SERIALIZABLE` PostgreSQL отвечает на такой конфликт ошибкой
       сериализации — повышать изоляцию для таких транзакций можно только вместе с повтором транзакции.
4. **Идентификаторы — UUID v4**, генерируются в приложении (`UUID.randomUUID()`), тип колонки `uuid`. Непредсказуемы
   и не раскрывают объём данных (FR-ISO-7). Исключение — справочные ключи, задаваемые оператором (версии документов, F-002/T02).
5. **Соединение:** параметры только из окружения: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (NFR-SEC-4). В основном
   `application.yaml` значений по умолчанию нет — без них старт падает с ошибкой разрешения плейсхолдера.
   Пароль не входит в URL. Таймауты: `spring.datasource.hikari.connection-timeout: 5s`, параметр драйвера
   `connectTimeout: 5` (сек.) — недоступная БД даёт быструю ошибку старта/health, а не зависание.
6. **Health:** `DataSourceHealthIndicator` (компонент `db`) входит в `/actuator/health`; показываются компоненты
   и их статусы без деталей (`show-components: always`, `show-details: never`).
7. **Тесты — Testcontainers 2.x через JDBC-URL** `jdbc:tc:postgresql:18-alpine:///auto_statistic?TC_DAEMON=true`
   в `src/test/resources/application-test.yaml`; профиль `test` включается для всех тестов одной строкой в
   `build.gradle.kts` (`tasks.test { systemProperty("spring.profiles.active", "test") }`). Контейнер один на JVM
   (переиспользуется всеми Spring-контекстами с этим URL), Flyway накатывает схему автоматически.
   Тестам, которым нужен собственный управляемый контейнер (остановка БД для проверки health), — отдельный
   `PostgreSQLContainer` с `@ServiceConnection` в `@TestConfiguration` (connection details важнее свойств).
   Изоляция данных между тестами — уникальными данными каждого теста (email вида `u-<uuid>@example.com`) и
   проверками только «своих» строк; глобальное состояние таблиц тесты не предполагают.
   Для сборки нужен запущенный Docker (документируется в `README.md`).
8. **Локальная разработка:** `compose.yaml` в корне (PostgreSQL 18, учётные данные только для разработки) и профиль
   `local` (`application-local.yaml`). `spring-boot-docker-compose` не подключается.

## Последствия
- (+) Полный контроль над SQL и типами PostgreSQL, нет магии маппинга value classes, домен без аннотаций.
- (+) Минимум зависимостей; JdbcClient входит в Spring Framework.
- (+) Тесты на настоящем PostgreSQL той же мажорной версии; существующие Spring-тесты не требуют аннотаций.
- (−) Шаблонный код маппинга и SQL пишется вручную; опечатки в SQL ловятся только интеграционными тестами —
  поэтому каждый репозиторий обязан иметь интеграционный тест (conventions «Тесты»).
- (−) Сборка требует Docker. Без Docker `./gradlew build` падает на интеграционных тестах.
- (−) `jdbc:tc:`-URL скрывает контейнер от Spring (нет `@ServiceConnection`); для особых сценариев — отдельный контейнер.

## Рассмотренные альтернативы
- **Spring Data JDBC** — агрегаты и репозитории «из коробки», но аннотации/конвенции на сущностях, ограниченная
  поддержка Kotlin value classes и типов `inet`, неявные запросы. Для наших простых запросов выигрыша нет.
- **jOOQ** — типобезопасный SQL, но кодогенерация из схемы требует БД во время сборки (Testcontainers в Gradle) и
  отдельной настройки; избыточно на текущем объёме.
- **Exposed** — Kotlin-DSL, но собственная модель транзакций рядом со Spring, меньше распространён в Spring-проектах.
- **JPA/Hibernate** — изменяемые сущности, ленивые загрузки, N+1; противоречит функциональному стилю.
- **`@ServiceConnection` в каждой спецификации** вместо `jdbc:tc:` — официальный путь Boot, но требует импорта
  тестовой конфигурации в каждый Spring-тест; оставлен для особых случаев.
- **PostgreSQL 17** — тоже поддерживается; 18 — актуальная мажорная версия на дату решения.
