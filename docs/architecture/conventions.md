# Стандарты кода

> Ведёт `tech-lead`, изменения согласуются с `architect`. Используется разработчиком при написании кода
> и тимлидом как чек-лист ревью.

## Функциональный стиль
- Неизменяемость по умолчанию: `val`, `data class`, read-only коллекции (`List`, `Map`). `var` и mutable-коллекции — только локально и обоснованно.
- Доменная логика — чистые функции без побочных эффектов; эффекты (БД, сеть, время, UUID) — на краях, передаются как зависимости.
- Ошибки бизнес-логики — значения, а не исключения: `Either<DomainError, T>` (Arrow Core, ADR-0008) или `sealed interface ...Outcome`,
  если неуспешных для вызывающего исходов нет. Исключения — только для действительно исключительных ситуаций инфраструктуры
  (БД недоступна): use case их не перехватывает, наружу — 500 без деталей.
- Arrow — только `arrow-core` версии из каталога (`arrow`), не новее совместимой с Kotlin проекта (ADR-0008): `Either`, `either { }`,
  `Raise`, `ensure`, `ensureNotNull`, `zipOrAccumulate`/`mapOrAccumulate`, `NonEmptyList`. Optics, fx, resilience не подключаются.
  Arrow и `shared.domain` — единственные разрешённые зависимости domain-слоя.
- Доменные ошибки — `sealed interface` с исчерпывающим `when` без `else`.
- Типобезопасность: value classes для идентификаторов и величин (`@JvmInline value class UserId(val value: UUID)`), без «голых» `String`/`Long` в доменных сигнатурах.
- Валидация на границе: «parse, don't validate» — из DTO строится валидный доменный тип через smart-конструктор, возвращающий `Either`.
- Без `!!`. Nullable обрабатывается явно (`?.let`, `?:`, `ensureNotNull`).
- Время и случайность — через зависимости: бин `java.time.Clock` (UTC), бизнес-дата — в зоне `Europe/Moscow`
  (`BusinessTime`); `Instant.now()`, `LocalDate.now()` без `Clock` и `SecureRandom` вне порта-генератора запрещены.
  UUID v4 генерируется в application/infrastructure, не в domain (ADR-0007).
- Композиция через `map` / `flatMap` / `either { }` блоки, без вложенных `if`-лестниц.
- Функции маленькие, одна ответственность; expression body, где читаемо.

## Spring
- Конструкторная инъекция, без `@Autowired` на полях; `lateinit` для бинов запрещён.
- Контроллеры тонкие: парсинг DTO → вызов use case → маппинг результата в HTTP-ответ (`Either` → статус) исчерпывающим `when`.
- Единый формат ошибок API (RFC 7807 `ProblemDetail`, ADR-0008): `type = urn:auto-statistic:problem:<code>`, `title`, `status`,
  `detail` (рус.), `instance`, расширение `code`; для `validation-failed` — `errors[]` `{field, code, message}` со всеми ошибками
  полей сразу. ProblemDetail проекта создаётся только фабрикой `shared.api.ApiProblems`; стек-трейсы, имена классов и SQL наружу не попадают.
- DTO запросов — nullable-поля (`String?`, `Boolean?`), разбор в доменные типы — smart-конструкторами с накоплением ошибок.
  Bean Validation (`spring-boot-starter-validation`) не подключается (ADR-0008).
- Транзакции — на уровне application-сервисов через `shared.application.TransactionRunner.inTransaction { … }`
  (`Left` → откат, ADR-0008). **`@Transactional` на методах, возвращающих `Either`, запрещён** (откатывает только по исключению);
  `@Transactional` не в контроллерах и не в domain.
- Побочные эффекты вне БД (письма, запись аудита) — только **после** выхода из `inTransaction`: блок возвращает описание того,
  что нужно сделать, use case исполняет его после коммита. Сбой письма/аудита не меняет ответ (ADR-0011, ADR-0012).
- Конфигурация — `@ConfigurationProperties` + data class, без `@Value` россыпью.
- Domain-слой не импортирует `org.springframework.*`.
- Межконтекстные зависимости (`legal`, `mail`, `account`, `audit`) — только application → application; в `shared` — только то,
  что нужно двум и более контекстам.
- Бины-фильтры Spring Security создаются внутри конфигурации цепочки, а не как `@Component`/`@Bean` (иначе Boot регистрирует
  их ещё и сервлет-фильтром).

## База данных
- PostgreSQL 18, миграции Flyway в `src/main/resources/db/migration` (`V<NNN>__<snake_case>.sql`, трёхзначный номер),
  миграции не редактируются после мержа (ADR-0007).
- Доступ к данным — Spring `JdbcClient`, SQL вручную в `…infrastructure.Jdbc*Repository`, маппинг строк — явными функциями
  в доменные типы. ORM, Spring Data, jOOQ не используются.
- Только именованные параметры (`:name`); конкатенация значений в SQL запрещена.
- `timestamptz` ↔ `Instant`: параметр — `OffsetDateTime.ofInstant(i, ZoneOffset.UTC)`, чтение — `getObject(col, OffsetDateTime::class.java)`;
  `inet` — `CAST(:ip AS inet)` / `host(ip)`.
- Нарушение уникальности как бизнес-исход (ADR-0007, п. 3) — **по умолчанию** `INSERT … ON CONFLICT (<колонки ключа>) DO NOTHING`
  с проверкой числа вставленных строк (`0` ⇒ `Either.Left` в репозитории). Цель конфликта указывается явно (прочие нарушения
  ограничений остаются исключениями); транзакция не ломается, после такого `Left` в ней можно продолжать и коммитить.
  Чтение «победившей» строки в той же транзакции корректно только при `READ COMMITTED` (умолчание `TransactionRunner`);
  повышать изоляцию для таких транзакций — только вместе с повтором транзакции.
- Перехват `DuplicateKeyException` → `Either.Left` — допустимый запасной путь: в PostgreSQL ошибка внутри транзакции переводит
  её в состояние aborted, поэтому такой `Left` обязан выйти из `inTransaction` (откат); превращать его в `Right`, выполнять
  после него операторы или коммитить ту же транзакцию нельзя.
- Каждый репозиторий имеет интеграционный тест на настоящем PostgreSQL (SQL проверяется только так).
- Секреты (пароли, токены) хранятся только хешами; в БД, логах и `toString()` value classes их нет.
- Явные индексы под запросы, внешние ключи, `NOT NULL` по умолчанию.
- Никаких N+1: проверять запросы в циклах.
- Деньги/точные величины — `NUMERIC`/`BigDecimal`, время — `timestamptz`/`Instant`.

## Сборка и зависимости
- Версии плагинов и библиотек — только в `gradle/libs.versions.toml`; в `build.gradle.kts` литералов версий нет. Библиотеки из Spring Boot BOM — без версии.
- Spring Boot BOM — через `platform(SpringBootPlugin.BOM_COORDINATES)`; плагин `io.spring.dependency-management` не подключается (ADR-0002).
- Репозитории — только в `settings.gradle.kts`.
- Обновление Kotlin / Gradle / detekt — одним согласованным шагом со сверкой по таблице совместимости detekt (detekt.dev → Compatibility Table)
  и KGP (kotlinlang.org); версия Kotlin должна совпадать с управляемой BOM Spring Boot (ADR-0002, ADR-0004).
- Spring Boot 4: стартеры `spring-boot-starter-webmvc` (не `-web`), тестовые модули `spring-boot-starter-<module>-test`; HTTP-тесты —
  `RestTestClient` (`@AutoConfigureRestTestClient`), не `TestRestTemplate`, в Kotest-спецификации (см. «Тесты»); моки бинов — `@MockitoBean`,
  не `@MockBean`. Примеры под Boot 3 и примеры на JUnit Jupiter не копировать.
- Kotest (ADR-0005): версия — одна запись `kotest` в каталоге, модули без версий через `platform(libs.kotest.bom)`; раннер — `kotest-runner-junit6`
  (major JUnit Platform должен совпадать с `junit-jupiter.version` BOM Boot). `tasks.test { useJUnitPlatform { includeEngines("kotest") } }`;
  `failOnNoDiscoveredTests` не отключать.
- Подавления проверок качества (`@Suppress("<DetektRule>")`, `@Suppress("ktlint:standard:<rule>")`, baseline detekt) — только точечные (на конкретном объявлении) и с комментарием-причиной;
  baseline-файлы и подавления на уровне файла/пакета запрещены без решения architect.

## Сборка и проверки
Стиль — ktlint 1.8.0 (ktlint-gradle, стиль `ktlint_official`), статический анализ — detekt 2.0.0-alpha.3 (ADR-0004).
Обе проверки входят в `check`, поэтому `build` падает при любом нарушении: и в main, и в test, и в `*.gradle.kts` (ktlint).

| Команда (Windows: `gradlew.bat …`) | Что делает |
|------------------------------------|------------|
| `./gradlew build`                  | Компиляция, тесты и все проверки (`ktlint*Check` + `detekt`) |
| `./gradlew ktlintCheck detekt`     | Только проверки, без тестов |
| `./gradlew ktlintFormat`           | Автоформатирование стилевых нарушений ktlint (detekt не автоисправляет) |
| `./gradlew detekt`                 | Только detekt. Удобно, когда ktlint падает раньше и скрывает находки detekt (или `build --continue`) |

- Отчёты: ktlint — `build/reports/ktlint/<задача>/`, detekt — `build/reports/detekt/` (`detekt.html`, `.xml`, `.sarif`, `.md`).
  Нарушения выводятся в консоль: ktlint — `файл:строка:колонка сообщение (standard:<правило>)`,
  detekt — `e: файл:строка:колонка сообщение [<Правило>]`.
- Конфигурация: `.editorconfig` (стиль ktlint, `max_line_length = 120`), `config/detekt/detekt.yml` — только дельта
  к дефолтам detekt (`buildUponDefaultConfig = true`). Полный конфиг можно сгенерировать `./gradlew detektGenerateConfig`, но в репозиторий его не коммитить.
- Подавления — только точечные (на конкретном объявлении) и с комментарием-причиной, без baseline (см. «Сборка и зависимости»).
- Неиспользуемые импорты контролирует ktlint `no-unused-imports`. В ktlint 1.8.0 правило deprecated и по умолчанию выключено, поэтому оно
  явно включено в `.editorconfig` (`ktlint_standard_no-unused-imports = enabled`, ADR-0004). Нарушение роняет `ktlint…Check`
  (main, test, `*.gradle.kts`), а `ktlintFormat` удаляет такой импорт. При переходе на ktlint 2.0 правило удалят — замену нужно выбрать до обновления.
  detekt `UnusedImport` для этого не используется: в detekt 2.0 правило требует type resolution (`detektMain`/`detektTest`),
  а в задаче `detekt` молча не выполняется, даже если включить его в `detekt.yml`.

## Тесты
- Каждая задача с доменной логикой / API / БД: unit-тесты доменной логики + интеграционный тест на API/репозиторий
  (для БД — Testcontainers + PostgreSQL, с F-002/T01). Технические задачи без домена и БД (бутстрап, инфраструктура
  сборки) покрываются интеграционными/смоук-тестами своих AC; отсутствие Testcontainers в них не нарушение.
- Фреймворк — только **Kotest** (ADR-0005): и раннер, и проверки. JUnit Jupiter (`@Test`, `org.junit.*`), AssertJ, Hamcrest,
  `kotlin.test` в тестах не используются — импорты запрещены detekt `ForbiddenImport` (с T03 F-001), движок JUnit Jupiter не исполняется.
- Стиль спецификаций — **`FunSpec`** (другие стили — только новым ADR):
  - `class <Субъект>Test(deps…) : FunSpec({ … })` — тело лямбдой в конструктор (без `init { }`), один класс-спецификация на файл,
    пакет зеркалирует main;
  - тест — `test("<поведение>") { … }`, имя — строка, описывающая поведение: `test("returns NotFound when trip does not exist")`;
    backtick-функции не используются; группировка — `context("<условие/субъект>") { … }`, вложенность ≤ 2 уровней;
  - проверки — Kotest-матчеры (`shouldBe`, `shouldNotBeNull`, `shouldThrow<…>`, матчеры коллекций/строк); `shouldNotBeNull()` вместо `!!`
    и вместо обращения к nullable/платформенным значениям без проверки.
- Spring-спецификации: `@SpringBootTest(...)` + `@ApplyExtension(SpringExtension::class)` (`io.kotest.extensions.spring`) на каждой
  спецификации; глобальная регистрация `SpringExtension` в `ProjectConfig` запрещена. Бины — параметрами конструктора спецификации
  (без `@Autowired`-полей и `lateinit`); класс вручную `open` не делать — его открывает `kotlin("plugin.spring")`; предупреждение
  «SpringExtension on a final class» — дефект конфигурации, не глушить.
- HTTP-тесты (Boot 4): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`, `RestTestClient` — через конструктор.
  Цепочки `expectStatus()/expectHeader()/expectBody()…` не используются как проверки: ответ забирается
  `….exchange().returnResult(<тип>)` (`EntityExchangeResult`: `status`, `responseHeaders`, `responseBody`) и проверяется Kotest-матчерами;
  JSON — в `Map<String, Any?>` через `ParameterizedTypeReference` или в DTO.
- Тесты независимы, без общего изменяемого состояния; given / when / then — структурой тела (пустые строки / короткие комментарии).
- БД в тестах (ADR-0007): профиль `test` (включается в `tasks.test`), `jdbc:tc:postgresql:18-alpine` — один контейнер на JVM для всех
  Spring-контекстов. Изоляция — уникальными данными (`support/TestData.uniqueEmail()`) и проверкой только «своих» строк; тест, меняющий
  глобальное состояние (останавливает БД, добавляет версии документов, удаляет строки по условию, перезапускает приложение), —
  на собственном контейнере (`support/PostgresContainers`, `DedicatedPostgresConfiguration`, `AppRunner`). Для сборки нужен Docker.
- Порты в unit-тестах подменяются простыми фейками (in-memory); MockK не подключается. `@MockitoBean` — только когда фейк-бин
  невозможен (в Kotlin `Mockito.any()` для non-null параметров даёт NPE — предпочитать `@TestConfiguration` с фейком).
- `Either` проверяется `kotest-assertions-arrow` (`shouldBeRight()`, `shouldBeLeft()`).
- Время — управляемое (`support/MutableClock` через `@TestConfiguration`); тест сам выставляет время в начале. Имена `@Bean` в тестовых
  конфигурациях не совпадают с именами бинов приложения (переопределение бинов в Boot выключено, `BeanDefinitionOverrideException`).
- Отсутствие секретов в логах проверяется `support/LogCapture`.
- Покрыты негативные и граничные сценарии из критериев приёмки.

## Чек-лист код-ревью
- [ ] Реализованы все критерии приёмки задачи
- [ ] Соответствует «Техническому плану» (или отклонения описаны и обоснованы)
- [ ] Слои не нарушены, domain без Spring
- [ ] Ошибки — типизированные значения, `when` исчерпывающие
- [ ] Нет `!!`, `var` без необходимости, mutable-состояния в бинах
- [ ] Транзакции — через `TransactionRunner`, нет `@Transactional` на `Either`; письма/аудит — после коммита
- [ ] Миграции корректны и обратно совместимы; SQL только с именованными параметрами; у репозитория есть интеграционный тест
- [ ] Тесты есть, осмысленны и проходят (`./gradlew build`, включая ktlint и detekt); только Kotest `FunSpec` и Kotest-матчеры, видны по имени в отчёте
- [ ] Версии — только в каталоге; подавления проверок точечные и с причиной, без baseline
- [ ] Нет секретов (в коде, логах, ответах, БД в открытом виде), SQL-инъекций, утечки внутренних ошибок в API; ошибки — через `ApiProblems`
- [ ] Читаемость: имена, размер функций, отсутствие дублирования
