# F-002/T01: Постоянное хранение данных и тестовое окружение

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | F-001 (все задачи)               |
| Оценка      | M                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Первая фича с данными. Приложению нужно постоянное хранилище, которое переживает перезапуски (PRD NFR-3), а тестам —
воспроизводимое окружение с хранилищем того же типа, что и в эксплуатации. Хранилище и способ доступа к нему выбирает
architect, по умолчанию в стеке — PostgreSQL. При развёртывании хранилище размещается в РФ (FR-PD-1): это требование
к эксплуатации, в задаче оно только фиксируется в документации.

## Критерии приёмки
- [ ] AC-1: Given пустое хранилище и настроенное подключение, When приложение запускается, Then структура данных
  создаётся автоматически, без ручных скриптов. При повторном запуске она не пересоздаётся и данные не теряются.
- [ ] AC-2: Given запущенное приложение, When запрошена проверка работоспособности, Then в ответе видно, что хранилище
  доступно. Given хранилище недоступно, When запрошена проверка, Then статус «не работает».
- [ ] AC-3: Given хранилище недоступно при старте, When приложение запускается, Then старт завершается понятной ошибкой
  без зависания и без вывода пароля подключения.
- [ ] AC-4: Given репозиторий, When просмотрены файлы конфигурации, Then параметры подключения (адрес, пользователь,
  пароль) задаются через окружение, а пароли эксплуатационного хранилища в репозитории отсутствуют.
- [ ] AC-5: Given чистый клон и окружение, описанное в документации, When выполнен `./gradlew build`, Then
  интеграционные тесты работают с хранилищем того же типа, что и в эксплуатации, без ручного создания схемы.
  Данные одного теста не влияют на другой.
- [ ] AC-6: Given документация проекта, When разработчик читает её, Then в ней описаны локальный запуск с хранилищем,
  требования к окружению для тестов и требование размещать эксплуатационное хранилище в РФ.

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
Решение — [ADR-0007](../../architecture/adr/ADR-0007-postgresql-flyway-jdbcclient.md), [architecture.md](./architecture.md)
(разделы «Модель данных», «Конфигурация», «Тестирование»). Кода приложения (бинов) задача не добавляет — только
зависимости, конфигурацию, миграцию, тестовую инфраструктуру и документацию.

- **Зависимости** (`gradle/libs.versions.toml`, все без версий — BOM Boot 4.1.2):
  - `implementation`: `org.springframework.boot:spring-boot-starter-jdbc`, `org.springframework.boot:spring-boot-starter-flyway`,
    `org.flywaydb:flyway-database-postgresql`; `runtimeOnly`: `org.postgresql:postgresql`;
  - `testImplementation`: `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`.
  - `build.gradle.kts`: в `tasks.test { … }` добавить `systemProperty("spring.profiles.active", "test")`.
- **Конфигурация:**
  - `src/main/resources/application.yaml` — добавить:
    ```yaml
    spring.datasource:
      url: ${DB_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      hikari:
        connection-timeout: 5s
        data-source-properties.connectTimeout: 5
    management.endpoint.health:
      show-components: always
      show-details: never
    ```
    Умолчаний для `DB_*` нет; Flyway — настройки по умолчанию (`classpath:db/migration`).
  - `src/main/resources/application-local.yaml` — `spring.datasource.url: jdbc:postgresql://localhost:5432/auto_statistic`,
    `username/password: auto_statistic` (только для локальной БД из `compose.yaml`; не эксплуатационный пароль).
  - `src/test/resources/application-test.yaml` — `spring.datasource.url: jdbc:tc:postgresql:18-alpine:///auto_statistic?TC_DAEMON=true`,
    `username: test`, `password: test`.
  - `compose.yaml` (корень): сервис `postgres`, образ `postgres:18-alpine`, `POSTGRES_DB/USER/PASSWORD = auto_statistic`,
    порт `5432:5432`, именованный том, смонтированный в `/var/lib/postgresql` (в образах 18+ каталог данных изменился —
    не `/var/lib/postgresql/data`), `healthcheck` на `pg_isready`.
- **Модель данных / миграции:** `src/main/resources/db/migration/V001__baseline.sql` — комментарий-заголовок
  («baseline: схема управляется Flyway, см. ADR-0007») и `SELECT 1;`. Таблиц не создаёт (первые таблицы — T02, T04).
- **Доменные типы и ошибки:** нет.
- **API-контракт:** `GET /actuator/health` — `200 {"status":"UP","components":{"db":{"status":"UP"},…}}`, у компонентов
  только `status` (нет `details`); при недоступной БД — `503`, `status: "DOWN"`, `components.db.status: "DOWN"`.
- **Тестовая инфраструктура** (`src/test/kotlin/com/autostatistic/support/`, используется всеми следующими задачами):
  - `PostgresContainers.newContainer(): PostgreSQLContainer` (`org.testcontainers.postgresql.PostgreSQLContainer`, `postgres:18-alpine`);
  - `DedicatedPostgresConfiguration` — `@TestConfiguration(proxyBeanMethods = false)`, `@Bean @ServiceConnection` контейнер
    (для `@SpringBootTest`, которым нужен свой контейнер);
  - `AppRunner.run(container, vararg args): ConfigurableApplicationContext` — `SpringApplication.run(AutoStatisticApplication, …)`
    с `--spring.profiles.active=test`, `--server.port=0` и `--spring.datasource.{url,username,password}` контейнера;
  - `LogCapture` — подключает Logback `ListAppender<ILoggingEvent>` к корневому логгеру на время блока, отдаёт
    отформатированные сообщения + текст исключений;
  - `TestData.uniqueEmail()`.
- **Тесты** (Kotest `FunSpec`, пакет `com.autostatistic.shared.infrastructure` — техническая инфраструктура БД):
  - `DatabaseMigrationTest` (AC-1): на собственном контейнере `AppRunner` запускает приложение → в `flyway_schema_history`
    есть запись `script = 'V001__baseline.sql'`, `success = true`; тест создаёт таблицу-пробу и строку; закрывает контекст, запускает снова →
    число строк истории и `installed_on` V001 не изменились, строка-проба на месте.
  - `DatabaseHealthTest` (AC-2, общий контейнер): `components.db.status == "UP"`, в теле нет ключа `details` ни на одном уровне.
  - `DatabaseHealthDownTest` (AC-2): `@SpringBootTest(RANDOM_PORT)` + `@Import(DedicatedPostgresConfiguration)` +
    `@DirtiesContext`; остановить контейнер → `GET /actuator/health` = `503`, `status: DOWN`, `components.db.status: DOWN`;
    ответ приходит быстрее 15 с (Hikari `connection-timeout` 5 s).
  - `DatabaseUnavailableStartupTest` (AC-3): `SpringApplication.run` с профилем `test`, `--spring.datasource.url=jdbc:postgresql://127.0.0.1:<свободный закрытый порт>/x`,
    `--spring.datasource.password=<уникальный маркер>`; старт бросает исключение за < 30 с (Kotest `timeout`), в цепочке есть
    `java.net.ConnectException` или `SQLException`/`FlywayException`; маркер пароля отсутствует и в сообщениях цепочки,
    и в `LogCapture`.
  - `DatasourceConfigurationTest` (AC-4): загрузить `classpath:application.yaml` через `YamlPropertySourceLoader` →
    `spring.datasource.url/username/password` равны `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}`.
  - Изменить `AutoStatisticApplicationTest`: тест «context loads without database» заменить на «context loads with
    DataSource and Flyway migrations applied» (`DataSource` и `Flyway` бины есть, `flyway.info().applied()` не пуст).
  - AC-5 обеспечивается всеми тестами выше в `./gradlew build`; правило изоляции данных — в README (уникальные данные,
    собственный контейнер для глобальных изменений).
- **Документация** (AC-6): новый `README.md` в корне — требования (JDK 17, Docker для `./gradlew build`), локальный запуск
  (`docker compose up -d`, `./gradlew bootRun --args='--spring.profiles.active=local'`), переменные окружения `DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD`, требование размещать эксплуатационную БД, её резервные копии и логи с ПДн в РФ (FR-PD-1),
  ссылка на ADR-0007. `docs/architecture/overview.md` уже обновлён архитектором.
- **Риски:** без Docker сборка красная (документировать); Windows + Docker Desktop — Testcontainers находит Docker
  автоматически, при проблемах — `DOCKER_HOST`/`TESTCONTAINERS_*` в README; `PortInUseTest`/`ServerPortOverrideTest`
  теперь тоже поднимают БД (через профиль `test`) — проверить, что они зелёные; старт тестов медленнее (контейнер один на JVM).

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: технический срез без бизнес-логики, размер M адекватен; закладывает инфраструктуру (`support/*`), на которую
  опираются T02–T07. AC проверяемы, у каждого есть конкретный тест; план согласован с ADR-0007 и conventions.
- Замечания (не блокируют, учесть при реализации):
  - [minor] Профиль `test` включается только системным свойством в `tasks.test`: запуск спецификации из IDE без Gradle
    (Kotest-плагин) не найдёт `DB_URL`. → В README в разделе про тесты указать «запускать через Gradle» (или настройку
    `spring.profiles.active=test` для IDE).
  - [minor] `DatabaseHealthDownTest` останавливает контейнер-бин, которым управляет Spring: убедиться, что закрытие контекста
    после `@DirtiesContext` не падает на повторном `stop()` (у Testcontainers он идемпотентен — достаточно не глушить ошибку).
  - [minor] `DatabaseUnavailableStartupTest`: свободный закрытый порт получать как в `PortInUseTest` (`ServerSocket(0)` →
    закрыть → использовать номер), а не константой.
- Синхронизация `conventions.md` с ADR-0007…0012 выполнена тимлидом (разделы «Функциональный стиль», «Spring»,
  «База данных», «Тесты», чек-лист) — правила обязательны с этой задачи.

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
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: PostgreSQL 18 + Flyway + JdbcClient + Testcontainers (ADR-0007), V001, профили local/test, compose.yaml, тестовая инфраструктура support/*, README |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY, 3 minor; conventions.md синхронизирован с ADR-0007…0012 |
