# Стандарты кода

> Ведёт `tech-lead`, изменения согласуются с `architect`. Используется разработчиком при написании кода
> и тимлидом как чек-лист ревью.

## Функциональный стиль
- Неизменяемость по умолчанию: `val`, `data class`, read-only коллекции (`List`, `Map`). `var` и mutable-коллекции — только локально и обоснованно.
- Доменная логика — чистые функции без побочных эффектов; эффекты (БД, сеть, время, UUID) — на краях, передаются как зависимости.
- Ошибки бизнес-логики — значения, а не исключения: `Either<DomainError, T>` (Arrow) или `sealed interface ...Result`. Исключения — только для действительно исключительных ситуаций инфраструктуры.
- Доменные ошибки — `sealed interface` с исчерпывающим `when` без `else`.
- Типобезопасность: value classes для идентификаторов и величин (`@JvmInline value class UserId(val value: UUID)`), без «голых» `String`/`Long` в доменных сигнатурах.
- Валидация на границе: «parse, don't validate» — из DTO строится валидный доменный тип через smart-конструктор, возвращающий `Either`.
- Без `!!`. Nullable обрабатывается явно (`?.let`, `?:`, `ensureNotNull`).
- Композиция через `map` / `flatMap` / `either { }` блоки, без вложенных `if`-лестниц.
- Функции маленькие, одна ответственность; expression body, где читаемо.

## Spring
- Конструкторная инъекция, без `@Autowired` на полях; `lateinit` для бинов запрещён.
- Контроллеры тонкие: парсинг DTO → вызов use case → маппинг результата в HTTP-ответ (`Either` → статус).
- Единый формат ошибок API (RFC 7807 `ProblemDetail`).
- Транзакции — на уровне application-сервисов, `@Transactional` не в контроллерах и не в domain.
- Конфигурация — `@ConfigurationProperties` + data class, без `@Value` россыпью.
- Domain-слой не импортирует `org.springframework.*`.

## База данных
- Схема меняется только миграциями Flyway (`V<NNN>__<описание>.sql`), миграции не редактируются после мержа.
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
  (для БД — Testcontainers + PostgreSQL, когда БД появится в проекте). Технические задачи без домена и БД (бутстрап, инфраструктура
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
- Покрыты негативные и граничные сценарии из критериев приёмки.

## Чек-лист код-ревью
- [ ] Реализованы все критерии приёмки задачи
- [ ] Соответствует «Техническому плану» (или отклонения описаны и обоснованы)
- [ ] Слои не нарушены, domain без Spring
- [ ] Ошибки — типизированные значения, `when` исчерпывающие
- [ ] Нет `!!`, `var` без необходимости, mutable-состояния в бинах
- [ ] Миграции корректны и обратно совместимы
- [ ] Тесты есть, осмысленны и проходят (`./gradlew build`, включая ktlint и detekt); только Kotest `FunSpec` и Kotest-матчеры, видны по имени в отчёте
- [ ] Версии — только в каталоге; подавления проверок точечные и с причиной, без baseline
- [ ] Нет секретов, SQL-инъекций, утечки внутренних ошибок в API
- [ ] Читаемость: имена, размер функций, отсутствие дублирования
