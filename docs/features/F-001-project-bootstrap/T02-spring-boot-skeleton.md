# F-001/T02: Каркас Spring Boot-приложения с проверкой работоспособности

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | DONE                             |
| Фича        | [F-001](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | F-001/T01                        |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
На основе Gradle-сборки из T01 создать минимальное Spring Boot-приложение (целевой стек из
`docs/architecture/overview.md`), которое запускается одной командой и сообщает о своей работоспособности
по HTTP. Это каркас, на котором будут разрабатываться фичи; бизнес-логики и подключения к БД здесь нет.

Допущение: каркас Spring Boot входит в «базовый проект» (Q1 в [feature.md](./feature.md)). Если пользователь
ответит, что нужен только Gradle-проект, — задача исключается из фичи.

Тесты задачи, включая тесты подъёма Spring-контекста и HTTP-эндпоинтов, пишутся на **Kotest** (дополнение
пользователя от 2026-10-23, см. [feature.md](./feature.md)); Kotest подключён в T01. Способ интеграции Kotest
со Spring и совместимость версий — решение architect.

## Критерии приёмки
- [x] AC-1: Given собранный проект, When запустить приложение стандартной командой Gradle (например,
  `./gradlew bootRun`), Then приложение стартует без ошибок и исключений в логе и слушает HTTP-порт.
- [x] AC-2: Given запущенное приложение, When выполнить HTTP GET на эндпоинт проверки работоспособности
  (конкретный путь определяет architect), Then ответ `200 OK` с признаком статуса «работает» (`UP`).
- [x] AC-3 (негативный): Given запущенное приложение, When выполнить запрос на несуществующий путь,
  Then ответ `404`, приложение продолжает работать.
- [x] AC-4: Given выполнена команда `./gradlew build`, When прогоняются тесты, Then есть Kotest-тест,
  проверяющий, что Spring-контекст приложения поднимается, и Kotest-тесты на AC-2 и AC-3 (HTTP-запросы к
  запущенному в тесте приложению); все проходят и видны по имени в отчёте о тестах.
- [x] AC-5: Given в окружении не настроена база данных, When запустить приложение и тесты, Then старт и
  тесты не требуют PostgreSQL (БД вне объёма фичи, Q2).
- [x] AC-6 (граничный): Given порт по умолчанию занят, When запустить приложение, Then старт завершается
  понятной ошибкой о занятом порте (без зависания); порт можно переопределить конфигурацией без изменения кода.
- [x] AC-7: Given код задачи в репозитории, When просмотреть тестовые исходники, Then все тесты задачи (контекст, HTTP,
  занятый порт) — Kotest-спецификации с проверками Kotest; тестов в стиле JUnit Jupiter (`@Test`) нет; smoke-тест
  из T01, если он удаляется, удалён без потери покрытия (в сборке выполняется ≥ 1 теста).
- [x] AC-8 (негативный): Given Spring-тест на Kotest с намеренно неверным ожиданием (например, ожидается статус
  `DOWN` или код `500` для `/actuator/health`; не коммитить), When выполнить `./gradlew build`, Then сборка падает
  (`BUILD FAILED`) с именем упавшего теста — т.е. Spring-тесты на Kotest реально выполняются, а не пропускаются.

## Открытые вопросы
- ~~Q1~~ — закрыт: пользователь подтвердил, каркас Spring Boot с health-check входит в фичу.
- ~~Web MVC vs WebFlux~~ — закрыт: Web MVC ([ADR-0003](../../architecture/adr/ADR-0003-spring-boot-web-mvc.md)).
- ~~Q7~~ — закрыт (ответ пользователя 2026-10-23): Kotest — и раннер, и assertions; JUnit-style тесты не пишутся.
- ~~Для architect: «Технический план» устарел относительно AC-4/AC-7/AC-8~~ — пересмотрен под Kotest 2026-10-23
  ([ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md): `kotest-extensions-spring`, совместимость с Boot 4.1.2 / Kotlin 2.3.21 / JDK 17 сверена).

<!-- ============ architect ============ -->
## Технический план
Общая картина — [architecture.md](./architecture.md) (разделы «API», «Потоки», «Как проверяется»); решения — ADR-0003,
[ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md) (Kotest + Spring). Пересмотрен 2026-10-23 под Kotest (AC-4, AC-7, AC-8).

- **Модули / пакеты:**
  1. `gradle/libs.versions.toml`:
     - `[versions]`: `spring-boot = "4.1.2"`; удалить `junit` (версию JUnit Platform теперь даёт BOM Boot — 6.0.3); `kotest = "6.2.6"` — без изменений;
     - `[plugins]`: `kotlin-spring` (`org.jetbrains.kotlin.plugin.spring`, version.ref kotlin), `spring-boot`
       (`org.springframework.boot`, version.ref spring-boot);
     - `[libraries]` (без версий, кроме BOM): `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`,
       `jackson-module-kotlin` (`tools.jackson.module:jackson-module-kotlin`), `kotlin-reflect` (`org.jetbrains.kotlin:kotlin-reflect`),
       `spring-boot-starter-webmvc-test`, `spring-boot-starter-actuator-test`, `spring-boot-resttestclient`
       (`org.springframework.boot:spring-boot-resttestclient`), `kotest-extensions-spring` (`io.kotest:kotest-extensions-spring`, версия из `kotest-bom`);
       оставить `junit-platform-launcher`, `kotest-bom`, `kotest-runner-junit6`, `kotest-assertions-core`; удалить `junit-bom`.
  2. `build.gradle.kts`:
     - плагины: `kotlin-jvm`, `kotlin-spring`, `spring-boot`. **Не** применять `io.spring.dependency-management` (ADR-0002).
       `kotlin-spring` обязателен и для тестов: открывает классы с `@SpringBootTest` (нужно `SpringExtension`, ADR-0005).
     - `implementation(platform(SpringBootPlugin.BOM_COORDINATES))` (импорт `org.springframework.boot.gradle.plugin.SpringBootPlugin`);
       `testImplementation` наследует `implementation`, отдельный `testImplementation(platform(...Boot...))` не нужен;
       `testImplementation(platform(libs.kotest.bom))` — остаётся из T01; `platform(libs.junit.bom)` — удалить;
     - `implementation`: webmvc, actuator, jackson-module-kotlin, kotlin-reflect;
       `testImplementation`: webmvc-test, actuator-test, resttestclient, kotest-runner-junit6, kotest-assertions-core, kotest-extensions-spring;
       `testRuntimeOnly`: junit-platform-launcher (версия — из BOM Boot);
     - `tasks.test { useJUnitPlatform { includeEngines("kotest") } … }` — **без изменений** из T01. Движок JUnit Jupiter, приходящий
       транзитивно из `spring-boot-starter-test` (через `*-test` стартеры), не исполняется; исключать артефакт не нужно;
     - `kotlin { jvmToolchain(17); compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property") } }`;
     - БД-стартеры (jdbc/data-jpa/flyway) **не подключать** (AC-5).
     - Проверка совместимости после изменения: `./gradlew dependencies --configuration testRuntimeClasspath` — `org.junit.platform:*` и
       `junit-jupiter-api` = 6.0.3, `org.springframework:spring-test`/`spring-context` = 7.0.x (подняты BOM с 5.3.39 из Kotest),
       `net.bytebuddy:byte-buddy` = 1.18.15, `kotlinx-coroutines-*` = 1.10.2. Расхождения — описать в «Реализации».
  3. `src/main/kotlin/com/autostatistic/AutoStatisticApplication.kt`:
     `@SpringBootApplication class AutoStatisticApplication` + top-level `fun main(args: Array<String>) { runApplication<AutoStatisticApplication>(*args) }`.
     Других классов/бинов/контроллеров не создавать.
  4. `src/main/resources/application.yaml`:
     ```yaml
     spring:
       application.name: auto-statistic
       mvc.problemdetails.enabled: true
     management:
       endpoints.web.exposure.include: health
       endpoint.health.show-details: never
     ```
     `server.port` не задавать (дефолт 8080; переопределение через `SERVER_PORT` / `--server.port` — штатно, AC-6).
  5. Тесты — Kotest-спецификации `FunSpec` (ADR-0005) в `src/test/kotlin/com/autostatistic/`. `BuildSmokeTest` из T01 **оставить**
     (не Spring; проверяет JDK toolchain, AC-7 — покрытие не теряется). Общие правила для Spring-спецификаций:
     - аннотации на классе: `@SpringBootTest(...)` (+ при необходимости `@AutoConfigureRestTestClient`) и
       `@ApplyExtension(SpringExtension::class)` (`io.kotest.core.extensions.ApplyExtension`, `io.kotest.extensions.spring.SpringExtension`);
     - зависимости — **параметрами конструктора** спецификации (расширение создаёт спеку через autowire конструктора); `@Autowired`-полей,
       `lateinit`, глобального `ProjectConfig` нет;
     - класс не объявлять `open` вручную — его открывает `kotlin-spring` по `@SpringBootTest`. В выводе теста не должно быть
       «Using SpringExtension on a final class» (если есть — плагин не применён; системным свойством не глушить);
     - проверки — только Kotest-матчеры (`io.kotest.matchers.*`). Цепочки `expectStatus()/expectHeader()/expectBody()…jsonPath()`
       `RestTestClient` **не** использовать как проверки: ответ забирается `…exchange().returnResult(<тип>)` (`EntityExchangeResult<T>`:
       `status`, `responseHeaders`, `responseBody`), далее `shouldBe`/`shouldNotBeNull`. JSON-тело — в `Map<String, Any?>` через
       `object : ParameterizedTypeReference<Map<String, Any?>>() {}` (штатный Jackson-конвертер понимает `application/*+json`,
       в т.ч. actuator v3 и `problem+json`). Статус сравнивать как `result.status.value() shouldBe 200` (или `HttpStatus.OK.value()`).

     Спецификации:
     - `AutoStatisticApplicationTest(context: ApplicationContext)` — `@SpringBootTest` (MOCK) + `@ApplyExtension(SpringExtension::class)`:
       - `test("context loads without database")` — `context.getBeanNamesForType(javax.sql.DataSource::class.java).shouldBeEmpty()` (AC-4, AC-5).
     - `HealthEndpointTest(client: RestTestClient)` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` +
       `@ApplyExtension(SpringExtension::class)`:
       - `test("health endpoint returns 200 with status UP")` — `GET /actuator/health` → status 200, `body["status"] shouldBe "UP"` (AC-2);
       - `test("unknown path returns 404 problem detail and application keeps running")` — `GET /does-not-exist` → 404,
         `responseHeaders.contentType.shouldNotBeNull().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) shouldBe true`,
         `body["status"] shouldBe 404`; затем повторный `GET /actuator/health` → 200 (AC-3);
       - `test("actuator endpoints other than health are not exposed")` — `GET /actuator/env` → 404.
     - `PortInUseTest` — **без** `@SpringBootTest` и без `SpringExtension` (обычный `FunSpec`), AC-6:
       - `test("startup fails with PortInUseException when the port is taken")`: занять порт `ServerSocket(0)` (wildcard-адрес, **не** loopback) в `use {}`;
         `val outcome = runCatching { SpringApplication.run(AutoStatisticApplication::class.java, "--server.port=$port") }`;
         если контекст всё же стартовал — `outcome.getOrNull()?.close()`; затем
         `generateSequence(outcome.exceptionOrNull().shouldNotBeNull()) { it.cause }.filterIsInstance<PortInUseException>().firstOrNull()
         .shouldNotBeNull().port shouldBe port` (тип причины, не текст сообщения). `PortInUseException` — `org.springframework.boot.web.server`.
         Тест одновременно доказывает переопределение порта аргументом.
     Форма кода — ориентир; имена тестов — строки-поведения, при необходимости уточнять, сохраняя смысл.
- **API-контракт:**
  | Метод | Путь | Ответ | Ошибки |
  |-------|------|-------|--------|
  | GET | `/actuator/health` | `200 {"status":"UP"}`; допускается поле `groups` (имена проб, Boot 4.1.2), детали не раскрываются (`show-details: never`) | `503 {"status":"DOWN"}` |
  | * | неизвестный путь | — | `404 application/problem+json` (`type`, `title`, `status`, `detail`, `instance`) |
- **Модель данных / миграции:** нет (AC-5).
- **Доменные типы и ошибки:** нет; бизнес-ошибок нет. Формат ошибок API — `ProblemDetail` (conventions.md).
- **Ключевые решения:** ADR-0003 (Boot 4.1.2, Web MVC, Actuator, ProblemDetail, Jackson 3); ADR-0002 (platform BOM вместо
  dependency-management); ADR-0005 (Kotest `FunSpec`, `kotest-extensions-spring` через `@ApplyExtension` на спецификацию, конструкторная
  инъекция, проверки Kotest). Пакет и класс приложения — `overview.md` «Координаты и структура пакетов».
- **Как проверить:**
  - AC-4/AC-7 — `./gradlew build`: в выводе и в `build/reports/tests/test/index.html` видны `AutoStatisticApplicationTest`, `HealthEndpointTest`
    (3 теста), `PortInUseTest`, `BuildSmokeTest` — все PASSED; нет «final class»-предупреждения; в `src/test` нет импортов `org.junit`/`org.assertj`.
  - AC-8 (не коммитить): в `HealthEndpointTest` ожидать `body["status"] shouldBe "DOWN"` → `BUILD FAILED`,
    `HealthEndpointTest > health endpoint returns 200 with status UP FAILED`, сообщение `expected:<"DOWN"> but was:<"UP">`.
  - Вручную (QA): `./gradlew bootRun` → в логе `Tomcat started on port 8080` и `Started AutoStatisticApplicationKt` (top-level `main`) без stacktrace;
    `curl -i localhost:8080/actuator/health`; `curl -i localhost:8080/nope`; второй экземпляр `./gradlew bootRun` при работающем первом →
    `Port 8080 was already in use`, процесс завершается; `./gradlew bootRun --args='--server.port=9090'`.
- **Риски:**
  - В Boot 4 тестовые утилиты вынесены в отдельные модули (`spring-boot-resttestclient`, `*-test` стартеры) — примеры под Boot 3
    (`TestRestTemplate`, `spring-boot-starter-web`) не использовать. Пакеты сверены tech-lead: `@AutoConfigureRestTestClient` —
    `org.springframework.boot.resttestclient.autoconfigure`; `PortInUseException` — `org.springframework.boot.web.server`.
  - `kotest-extensions-spring` официально тестируется на Spring 5.3; используемые им API `TestContextManager` и
    `AutowireCapableBeanFactory.autowire(Class, AUTOWIRE_CONSTRUCTOR, true)` присутствуют в Spring 7.0.10 без deprecation (ADR-0005).
    Если расширение реально не работает — разработчик **сам, без эскалации и без согласования с пользователем** (решение пользователя
    2026-10-23) применяет запасной вариант из [ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md), раздел «Запасной вариант»:
    1. *Триггер* (все условия): Spring-спека падает/не исполняется со стеком в `io.kotest.extensions.spring.*` или вызовах
       `TestContextManager` из него (`NoSuchMethodError`/`NoClassDefFoundError`/`AbstractMethodError`, спека не инстанцируется,
       тесты skipped, AC-8 не роняет сборку); `dependencies --configuration testRuntimeClasspath` совпадает с п.2 плана; `kotlin-spring`
       применён, импорты верные; **контрольный прогон** той же проверки на JUnit Jupiter (по п.3–5 ниже) — зелёный. Если и JUnit-вариант
       падает — причина не в расширении, запасной вариант не применяется.
    2. *Что на JUnit:* только `AutoStatisticApplicationTest` и `HealthEndpointTest` (классы с `@SpringBootTest`). `BuildSmokeTest`,
       `PortInUseTest` — без изменений (Kotest `FunSpec`).
    3. *Сборка:* удалить `kotest-extensions-spring` из `[libraries]` и `testImplementation`; добавить в каталог
       `junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }` (без версии — BOM Boot, 6.0.3) и `testImplementation(libs.junit.jupiter)`;
       `tasks.test { useJUnitPlatform { includeEngines("kotest", "junit-jupiter") } }`. Остальное по п.1–2 плана без изменений.
    4. *Форма теста:* `@SpringBootTest(...)` (+ `@AutoConfigureRestTestClient` для `HealthEndpointTest`) +
       `@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)` на классе; `class HealthEndpointTest(private val client: RestTestClient)`;
       методы ``@Test fun `health endpoint returns 200 with status UP`() { … }`` — те же имена-поведения, тело-блок (не expression body).
       `@ApplyExtension`, `@Autowired`, `lateinit`, `@ExtendWith` — не нужны/запрещены (JUnit `SpringExtension` уже в `@SpringBootTest`).
    5. *Проверки:* без изменений — `exchange().returnResult(…)` + Kotest-матчеры (`shouldBe`, `shouldNotBeNull`, `shouldBeEmpty`);
       `org.junit.jupiter.api.Assertions/Assumptions`, AssertJ, `expect*`-цепочки — запрещены. Импорты JUnit — только `org.junit.jupiter.api.Test`.
    6. *Документы (в этой же задаче):* правки `conventions.md` («Сборка и зависимости» — пункт Kotest и пункт Spring Boot 4: «HTTP-тесты …
       в Kotest-спецификации», «примеры на JUnit Jupiter не копировать»; «Тесты» — пункты «Фреймворк», «Стиль спецификаций — `FunSpec`»
       (исключение из «backtick-функции не используются» для `@SpringBootTest`-классов), «Spring-спецификации», «HTTP-тесты»; чек-лист),
       строка «Тесты» в `overview.md`, `architecture.md` F-001 (строка «Нет JUnit/AssertJ-тестов» таблицы «Как проверяется» —
       `includeEngines("kotest", "junit-jupiter")`; риск «Транзитивный JUnit Jupiter»), отметка о применении в шапке ADR-0005 —
       точный перечень в ADR-0005. Конфигурация detekt `ForbiddenImport` для этого случая (белый список `org.junit.jupiter.api.Test`) — в T03. В «Реализации» → «Отклонения от плана»: ошибка (исключение + значимые строки стека), команда
       воспроизведения, версии из `dependencies`, результат контрольного прогона.
    7. *Как проверяются AC при запасном варианте:* AC-4, AC-8 — как в «Как проверить», имя в отчёте с `()`:
       `HealthEndpointTest > health endpoint returns 200 with status UP() FAILED`. AC-7 проверяется в редакции решения пользователя
       (см. feature.md, Q8): `@Test` допускается **только** в классах с `@SpringBootTest`, проверки во всех тестах — Kotest-матчеры,
       тесты без Spring — Kotest-спецификации.
    Иные обходы (`@Autowired lateinit`, ручной подъём контекста в `FunSpec`, перевод всех тестов на JUnit, отключение тестов) запрещены.
  - Bean `RestTestClient` от `@AutoConfigureRestTestClient` для RANDOM_PORT берёт порт из `local.server.port`; конструкторная
    инъекция происходит после старта контекста — порт уже известен. Если бин не находится — сверить с документацией Boot 4.1 «Testing»
    и описать в «Реализации».
  - Detekt-правило `SpreadOperator` на `*args` будет актуально в T03 (там разрешено точечное подавление).

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Проверено (2026-10-23, по артефактам Spring Boot 4.1.2 в Maven Central и документации):
  - Boot 4.1.2: Java 17–27, Gradle 8.14+/9.x; BOM управляет Kotlin 2.3.21, JUnit Jupiter 6.0.3, Jackson 3.1.7, Tomcat 11.0.27 —
    совпадает с каталогом T01, расхождения KGP/BOM нет.
  - Артефакты существуют: `spring-boot-starter-webmvc`, `-actuator`, `-webmvc-test`, `-actuator-test`, `spring-boot-resttestclient`.
    `@AutoConfigureRestTestClient` — `org.springframework.boot.resttestclient.autoconfigure` (модуль `spring-boot-resttestclient`);
    `PortInUseException` — `org.springframework.boot.web.server` (модуль `spring-boot-web-server`, транзитивно через webmvc-стартер);
    свойство `spring.mvc.problemdetails.enabled` есть в метаданных `spring-boot-webmvc` 4.1.2. Риски из плана сняты.
  - AC-1..AC-6 покрыты: AC-2/3/4 — автотесты, AC-5 — отсутствие БД-стартеров + контекстный тест, AC-6 — `PortInUseTests`
    (заодно доказывает переопределение порта); AC-1 и «нет исключений в логе» — ручная проверка QA (`bootRun`).
  - Соответствие ADR-0002/0003 и conventions.md: platform-BOM без dependency-management, тонкий каркас без бинов, ProblemDetail с первого дня,
    имена тестов — поведение в backticks.
- Замечания (не блокируют; учесть при реализации):
  - [minor] п.2: `testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))` избыточен — `testImplementation` наследует `implementation`,
    BOM уже применяется к test-classpath. → Можно не добавлять (меньше шума); если оставить — не ошибка.
  - [minor] п.1: `spring-boot-resttestclient` уже приходит транзитивно из `spring-boot-starter-webmvc-test` 4.1.2. Явное объявление допустимо
    (тест использует его API напрямую) — оставить, это осознанно.
  - [minor] п.5 `PortInUseTests`: занимать порт именно `ServerSocket(0)` (wildcard-адрес, как у Tomcat), **не** на loopback — иначе на
    некоторых ОС bind Tomcat на `0.0.0.0` может пройти и тест станет нестабильным. Проверять тип причины через `generateSequence(e) { it.cause }`,
    а не по тексту сообщения.
  - [minor] Конвенция «unit + интеграционный тест на Testcontainers + PostgreSQL» к T02 не применима (БД и доменной логики нет, AC-5) —
    уточнено в conventions.md (раздел «Тесты»), отсутствие Testcontainers не является нарушением.

### Ревью плана под Kotest (2026-10-23)
- Вердикт: **READY**
- Проверено по артефактам (байткод `kotest-extensions-spring-jvm` 6.2.6, `kotest-framework-engine-jvm` 6.2.6, `spring-test`/`spring-beans` 7.0.10,
  BOM `spring-boot-dependencies` 4.1.2):
  - BOM Boot 4.1.2: `spring-framework` 7.0.10, `junit-jupiter` 6.0.3, `kotlin` 2.3.21, `kotlin-coroutines` 1.10.2, `byte-buddy` 1.18.15 —
    совпадает с ADR-0005. `kotest-extensions-spring` объявляет `spring-context`/`spring-test` 5.3.39 и `byte-buddy` 1.18.11 только как **runtime** —
    BOM Boot (через `implementation(platform(...))`, наследуется `testRuntimeClasspath`) поднимает их до 7.0.10 / 1.18.15; на compile classpath Spring
    приходит из `*-test` стартеров. Проверка `dependencies --configuration testRuntimeClasspath` в плане — правильная.
  - Все API Spring, которые вызывает расширение (`TestContextManager(Class)`, `getTestContext`, `beforeTestClass`, `prepareTestInstance`,
    `beforeTestMethod`, `beforeTestExecution`, `afterTestExecution`, `afterTestMethod`, `afterTestClass`, `TestContext.getApplicationContext`,
    `AutowireCapableBeanFactory.autowire(Class,int,boolean)`) присутствуют в Spring 7.0.10 с теми же сигнатурами — `NoSuchMethodError` не ожидается.
  - Конструкторная инъекция через `@ApplyExtension` работает: `SpringExtension` (класс с no-arg конструктором) реализует `ConstructorExtension`;
    `SpecRefExecutor` регистрирует расширения из `@ApplyExtension` в реестре для класса спецификации **до** инстанцирования, а `SpecInstantiator`
    берёт `ConstructorExtension` из project config **и** из реестра для этого класса. Значит `class HealthEndpointTest(client: RestTestClient)` создастся
    через `autowire(…, AUTOWIRE_CONSTRUCTOR)`, а не упадёт на отсутствии no-arg конструктора; чистые спеки (`PortInUseTest`, `BuildSmokeTest`) Spring не трогают.
  - `RestTestClient.ResponseSpec.returnResult(Class<T>)` / `returnResult(ParameterizedTypeReference<T>)` → `EntityExchangeResult<T>` с `getStatus(): HttpStatusCode`,
    `getResponseHeaders()`, `getResponseBody()` — есть в `spring-test` 7.0.10; план проверок через Kotest-матчеры реализуем.
  - Риск «kotest-extensions-spring на Spring 7»: оценка — **приемлемый**; бинарная совместимость подтверждена, план эскалации без обходов описан.
  - AC-1..AC-8 покрыты; AC-8 (негатив) — `body["status"] shouldBe "DOWN"`. Согласовано с ADR-0003/0005 и обновлённым conventions.md.
- Замечания (не блокируют; учесть при реализации):
  - [minor] `responseBody` у `EntityExchangeResult<T>` — Java-геттер без nullability-аннотаций (платформенный тип). Обращение `body["status"]` скомпилируется,
    но при пустом теле даст NPE вместо понятного сообщения теста. → `val body = result.responseBody.shouldNotBeNull()` перед проверками полей (закреплено в conventions.md).
  - [minor] `AutoStatisticApplicationTest`: `getBeanNamesForType(...)` возвращает `Array<String>` — использовать матчер для массивов
    (`io.kotest.matchers.collections.shouldBeEmpty` имеет перегрузку для `Array<T>`), не конвертировать в список ради проверки.

### Ревью запасного варианта ADR-0005 (Spring-тесты на JUnit Jupiter) (2026-10-23)
- Вердикт: **READY** (замечания к `ForbiddenImport` относятся к T03, см. её «Ревью плана»)
- Проверено по байткоду `spring-test` 7.0.10 и `detekt-rules-style` 2.0.0-alpha.3:
  - `@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)` в Spring 7.0.10 есть, не deprecated. `TestConstructorUtils.isAutowirableConstructor`
    ищет аннотацию через `TestContextAnnotationUtils.findMergedAnnotation` на тест-классе; при `ALL` все параметры конструктора резолвит
    `SpringExtension` (JUnit) из контекста. Kotlin-класс `class HealthEndpointTest(private val client: RestTestClient)` имеет ровно один
    несинтетический конструктор — требование JUnit Jupiter выполнено (если появятся параметры со значениями по умолчанию, Kotlin добавит
    синтетический конструктор — JUnit его игнорирует). Без аннотаций на параметрах никакого влияния `-Xannotation-default-target=param-property` нет.
    Конструктор вызывается после загрузки контекста, поэтому для `RANDOM_PORT` порт `RestTestClient` уже известен. Обоснование «без `@Autowired`
    на параметре» (аннотация попала бы и на поле, и поле повторно заполнил бы `DependencyInjectionTestExecutionListener`) корректно.
  - `kotlin-spring` открывает класс и методы по `@SpringBootTest`; JUnit это не нужно, но и не мешает. Требование «тело-блок, не expression body» верное:
    Jupiter не исполняет `@Test`-методы с не-`void` результатом.
  - `includeEngines("kotest", "junit-jupiter")`: оба ID движков верные. Jupiter находит только классы с `@Test`, `FunSpec` без таких методов
    он не трогает; Kotest находит только наследников `Spec`, поэтому двойного исполнения нет. `failOnNoDiscoveredTests` остаётся включённым.
    Имя в отчёте `… returns 200 with status UP()` (с `()`) — так Gradle показывает методы Jupiter, AC-8 при этом проверяем.
  - `testImplementation(libs.junit.jupiter)` дублирует транзитивный `junit-jupiter` из `spring-boot-starter-test`, но явное объявление API,
    который используется напрямую, допустимо (как с `spring-boot-resttestclient`). Версия берётся из BOM, литерала нет — соответствует conventions.
  - Условия срабатывания проверяемы и защищают от ложного перехода: нужны совпадение `dependencies`, верные импорты/плагин и зелёный контрольный прогон
    на JUnit. Ограничен круг переводимых классов (только `@SpringBootTest`), запрещённые обходы перечислены. Обязательные записи в «Отклонениях» достаточны для ревью кода.
- Замечания (не блокируют; при срабатывании запасного варианта учесть в той же задаче):
  - [minor] ADR-0005, «Обязательные правки документов»: в перечне не хватает пунктов `conventions.md`, которые противоречат запасному варианту:
    (а) «Сборка и зависимости», пункт Spring Boot 4 — «HTTP-тесты — `RestTestClient` … в Kotest-спецификации», «примеры на JUnit Jupiter не копировать»;
    (б) «Тесты», пункт «Стиль спецификаций — `FunSpec`» — «backtick-функции не используются» (в Spring-тестах запасного варианта будут
    backtick-методы). → architect: дополнить перечень в ADR-0005 и п.6 «Рисков» T02. При срабатывании разработчик правит и эти пункты
    (в (б) — исключение для `@SpringBootTest`-классов). Также обновить строку «Нет JUnit/AssertJ-тестов» в таблице «Как проверяется»
    `architecture.md` F-001: там `includeEngines("kotest")`.

<!-- ============ kotlin-backend-developer ============ -->
## Реализация
- **Изменённые файлы:**
  - `gradle/libs.versions.toml` — `spring-boot = "4.1.2"`; удалены версия `junit` и `junit-bom`; плагины `kotlin-spring`, `spring-boot`;
    библиотеки (без версий) `spring-boot-starter-webmvc`, `-actuator`, `jackson-module-kotlin`, `kotlin-reflect`, `spring-boot-starter-webmvc-test`,
    `-actuator-test`, `spring-boot-resttestclient`, `kotest-extensions-spring`.
  - `build.gradle.kts` — плагины `kotlin-jvm` + `kotlin-spring` + `spring-boot` (без `io.spring.dependency-management`);
    `implementation(platform(SpringBootPlugin.BOM_COORDINATES))`; зависимости по п.2 плана; `compilerOptions` с `-Xjsr305=strict`,
    `-Xannotation-default-target=param-property`; `tasks.test` без изменений из T01 (`includeEngines("kotest")`, `exceptionFormat = TestExceptionFormat.FULL`).
  - `src/main/kotlin/com/autostatistic/AutoStatisticApplication.kt` — `@SpringBootApplication class AutoStatisticApplication` + top-level `main`.
  - `src/main/resources/application.yaml` — ровно по п.4 плана, `server.port` не задан.
  - `src/test/kotlin/com/autostatistic/AutoStatisticApplicationTest.kt`, `HealthEndpointTest.kt`, `PortInUseTest.kt` — новые Kotest-спецификации `FunSpec`.
    `BuildSmokeTest.kt` (T01) — без изменений.
- **Что сделано:** минимальный каркас Spring Boot 4.1.2 / Web MVC / Actuator (только `health`, без деталей), `ProblemDetail` для ошибок
  (404 → `application/problem+json`), БД-стартеров нет. Бинов/контроллеров сверх класса приложения нет.
  Основной путь ADR-0005 сработал: `kotest-extensions-spring` 6.2.6 на Spring 7.0.10 работает, конструкторная инъекция через
  `@ApplyExtension(SpringExtension::class)` — работает. **Запасной вариант (JUnit Jupiter) не применялся**, документы не правились.
  Учтены minor «Ревью плана»: отдельного `testImplementation(platform(Boot BOM))` нет; `spring-boot-resttestclient` объявлен явно;
  `ServerSocket(0)` на wildcard-адресе, причина ищется по типу через `generateSequence { it.cause }`; `responseBody.shouldNotBeNull()` перед
  обращением к полям; `getBeanNamesForType(...)` проверяется матчером для `Array` (`shouldBeEmpty`) без конвертации.
- **Тесты:** `./gradlew build` (Windows: `gradlew.bat build`) — **BUILD SUCCESSFUL, 6 тестов, 6 PASSED, 0 skipped/failed**:
  `AutoStatisticApplicationTest > context loads without database` (AC-4, AC-5);
  `HealthEndpointTest > health endpoint returns 200 with status UP` (AC-2), `> unknown path returns 404 problem detail and application keeps running` (AC-3),
  `> actuator endpoints other than health are not exposed`; `PortInUseTest > startup fails with PortInUseException when the port is taken` (AC-6);
  `BuildSmokeTest > tests run on the JDK 17 toolchain` (T01). Предупреждения «SpringExtension on a final class» нет; импортов
  `org.junit`/`org.assertj`/`org.hamcrest`/`kotlin.test`, `@Test`, `@Autowired`, `lateinit`, `!!` в `src/` нет (AC-7).
  Проверка зависимостей (`./gradlew dependencies --configuration testRuntimeClasspath`): `org.junit.platform:*` и `junit-jupiter-api` = 6.0.3,
  `spring-test`/`spring-context` 5.3.39 → 7.0.10, `byte-buddy` → 1.18.15, `kotlinx-coroutines-core` = 1.10.2 — совпадает с п.2 плана.
  Негативные и ручные проверки (2026-10-23, Windows 11, JDK 17.0.11):
  - AC-8: в `HealthEndpointTest` временно `["status"] shouldBe "DOWN"` → `BUILD FAILED`, `HealthEndpointTest > health endpoint returns 200 with status UP FAILED`,
    `org.opentest4j.AssertionFailedError: expected:<DOWN> but was:<UP>`, «6 tests completed, 1 failed». Изменение откатано, повторный `clean build` зелёный.
  - AC-6 (вручную): на машине порт 8080 занят сторонним процессом (Apache) → `./gradlew bootRun` за ~5 с завершился с
    «Web server failed to start. Port 8080 was already in use.» без зависания. Второй `bootRun --args='--server.port=9090'` при работающем
    первом на 9090 → та же понятная ошибка про 9090, процесс завершился (exit 1).
  - AC-1/AC-2/AC-3 (вручную, `./gradlew bootRun --args='--server.port=9090'` — заодно переопределение порта без изменения кода):
    `Tomcat started on port 9090`, `Started AutoStatisticApplicationKt in 2.852 seconds`, слов `exception` в логе — 0;
    `GET /actuator/health` → `200`, `application/vnd.spring-boot.actuator.v3+json`, `{"groups":["liveness","readiness"],"status":"UP"}`;
    `GET /nope` → `404`, `application/problem+json`, `{"detail":"No static resource nope.","instance":"/nope","status":404,"title":"Not Found"}`;
    после этого `GET /actuator/health` → `200`; `GET /actuator/env` → `404`.
- **Отклонения от плана:**
  - Нет отклонений в коде. Замечания для QA: (1) в логе старта строка — `Started AutoStatisticApplicationKt` (имя main-класса top-level
    функции `main`), а не `Started AutoStatisticApplication` из «Как проверить»; (2) ответ health в Boot 4.1.2 содержит также
    `"groups":["liveness","readiness"]` (детали индикаторов не раскрываются, `status` = `UP`) — тест проверяет только `status`;
    (3) на машине разработчика 8080 занят Apache, поэтому AC-1/2/3 проверены вручную на 9090.
  - В тестах есть строка JVM `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath
    has been appended` — от самоподключаемого агента byte-buddy/Mockito из `spring-boot-starter-test`; на результат не влияет.

<!-- ============ tech-lead ============ -->
## Ревью кода
- Вердикт: **IN_TESTING** (0 blocker, 0 major, 3 minor — не блокируют)
- Проверено (2026-10-23, Windows 11, JDK 17.0.11):
  - `./gradlew clean build` — **BUILD SUCCESSFUL**, 6/6 PASSED, видны по имени: `AutoStatisticApplicationTest > context loads without database`,
    `HealthEndpointTest` (3 теста), `PortInUseTest > startup fails with PortInUseException when the port is taken`, `BuildSmokeTest`. В выводе и
    `build/test-results` нет «SpringExtension on a final class»; строка JVM «Sharing is only supported for boot loader classes…» — от агента
    byte-buddy/Mockito, на результат не влияет.
  - Основной путь ADR-0005 реализован: `kotest-extensions-spring` (из `kotest-bom`, без версии) + `@ApplyExtension(SpringExtension::class)` на каждой
    `@SpringBootTest`-спецификации, бины — параметрами конструктора (`ApplicationContext`, `RestTestClient`); `ProjectConfig`, `@Autowired`, `lateinit`,
    ручного `open` нет; класс открывает `kotlin-spring`. Запасной вариант не применялся — JUnit Jupiter в каталоге/`testImplementation` нет,
    `includeEngines("kotest")`, документы не правились — корректно.
  - `tasks.test` сохранён из T01: `useJUnitPlatform { includeEngines("kotest") }`, `exceptionFormat = TestExceptionFormat.FULL`, `failOnNoDiscoveredTests` не отключён.
  - Сборка по плану п.1–2: версии только в каталоге (`kotlin`, `spring-boot`, `kotest`), Boot BOM через `platform(SpringBootPlugin.BOM_COORDINATES)`,
    `io.spring.dependency-management` нет, `junit-bom`/версия `junit` удалены, БД-стартеров нет, `compilerOptions` по плану, репозитории — только в `settings.gradle.kts`.
  - Main / `application.yaml` — ровно по п.3–4 плана; лишних бинов/контроллеров нет; `server.port` не задан.
  - Тесты: только Kotest `FunSpec` и Kotest-матчеры; `exchange().returnResult(ParameterizedTypeReference)` без `expect*`-цепочек; `responseBody.shouldNotBeNull()`
    перед полями; `shouldBeEmpty` для `Array`; `PortInUseTest` — `ServerSocket(0)` (wildcard), причина по типу через `generateSequence { it.cause }`,
    контекст закрывается при неожиданном успехе. Импортов `org.junit`/`org.assertj`/`org.hamcrest`/`kotlin.test`, `@Test`, `@Autowired`, `lateinit`, `!!` в `src/` нет.
  - Ручная проверка (`java -jar build/libs/auto-statistic-0.0.1-SNAPSHOT.jar --server.port=18081`): `Tomcat started on port 18081`,
    `Started AutoStatisticApplicationKt`, исключений в логе нет; `GET /actuator/health` → 200 `{"groups":["liveness","readiness"],"status":"UP"}`;
    `GET /nope` → 404 `application/problem+json`; `POST /actuator/health` → 405 `problem+json`; второй экземпляр на том же порте →
    `APPLICATION FAILED TO START … Port 18081 was already in use.`, процесс завершился. Утечки внутренних ошибок нет (стандартный `ProblemDetail`).
- Оценка замечаний разработчика для QA:
  1. `Started AutoStatisticApplicationKt` вместо `Started AutoStatisticApplication` — **не дефект**: Boot выводит имя класса, где объявлен `main`; для top-level
     `fun main` это `AutoStatisticApplicationKt` (так требует п.3 плана). QA принимать `Started AutoStatisticApplicationKt` как соответствие AC-1.
  2. Поле `groups` в ответе health — **не дефект** AC-2 (требует `200` и `status = UP`): это имена групп probes `liveness`/`readiness`, без деталей индикаторов
     (`show-details: never` соблюдён). Дополнительное поле аддитивно, контракт для клиентов не ломает.
  3. Порт 8080 занят Apache на машине разработчика — **не дефект**: AC-6 требует понятной ошибки и переопределения порта; проверено на 8080 (Apache), 9090 и
     в автотесте на случайном порте. QA проверяет AC-1/2/3 на свободном порте (`--server.port=…`), а сценарий «занятый 8080» — при наличии свободного 8080 двумя `bootRun`.
- Замечания:
  - [minor] `docs/features/F-001-project-bootstrap/architecture.md:109`, `:128`, `docs/architecture/adr/ADR-0003-spring-boot-web-mvc.md:16`, «API-контракт» этой задачи —
    тело health указано как `{"status":"UP"}`, фактически Boot 4.1.2 отдаёт ещё `"groups":["liveness","readiness"]` → architect: уточнить контракт
    («`status` = `UP`; допускается поле `groups`»). Если поле нежелательно — отдельным решением (`management.endpoint.health.probes.enabled: false`), не в рамках T02.
  - [minor] `docs/features/F-001-project-bootstrap/architecture.md:127`, «Как проверить» этой задачи — ожидаемая строка лога `Started AutoStatisticApplication`
    → architect: исправить на `Started AutoStatisticApplicationKt`.
  - [minor] `docs/features/F-001-project-bootstrap/feature.md:85` — в таблице задач T02 оставался `READY` (разработчик не обновил при переходе в работу/ревью)
    → обновлено tech-lead до `IN_TESTING`; разработчику впредь обновлять строку задачи в `feature.md` при смене статуса.

<!-- ============ qa-engineer ============ -->
## Тестирование
Окружение: 2026-10-23, Windows 11, JDK 17.0.11, Gradle wrapper; порт 8080 занят Apache, поэтому ручные проверки шли на 18090/18091.

| # | Кейс | Проверяет AC | Тип (auto/manual) | Результат |
|---|------|--------------|-------------------|-----------|
| 1 | `./gradlew bootRun --args='--server.port=18090'`: в логе `Tomcat started on port 18090`, `Started AutoStatisticApplicationKt in 3.194 seconds`, нет `Exception`/`ERROR` | AC-1 | manual | PASS |
| 2 | `AutoStatisticApplicationTest > context loads without database` (контекст поднимается) | AC-1, AC-4, AC-5 | auto | PASS |
| 3 | `HealthEndpointTest > health endpoint returns 200 with status UP` | AC-2 | auto | PASS |
| 4 | `curl -i :18090/actuator/health` → `200`, `{"groups":["liveness","readiness"],"status":"UP"}` (`groups` — не дефект, см. «Ревью кода») | AC-2 | manual | PASS |
| 5 | `HealthEndpointTest > health endpoint does not expose component details` — нет `components`/`details` (`show-details: never`) **(добавлен QA)** | AC-2 (граничный) | auto | PASS |
| 6 | `HealthEndpointTest > unknown path returns 404 problem detail and application keeps running` | AC-3 | auto | PASS |
| 7 | `HealthEndpointTest > 404 problem detail contains title and instance without internal error data` — `title`, `instance`, нет `trace`/`exception` **(добавлен QA)** | AC-3 | auto | PASS |
| 8 | `HealthEndpointTest > unsupported method on health returns 405 problem detail and application keeps running` — `POST /actuator/health` → 405 `problem+json`, затем health 200 **(добавлен QA)** | AC-3 (негативный) | auto | PASS |
| 9 | `curl :18090/nope` → 404 `application/problem+json`; `/actuator/env` → 404; `POST /actuator/health` → 405; после них health → 200 | AC-3 | manual | PASS |
| 10 | `HealthEndpointTest > actuator endpoints other than health are not exposed` | AC-3 | auto | PASS |
| 11 | Отчёт `./gradlew clean build`: все спецификации видны по имени, `build/test-results` — skipped/failures/errors = 0 | AC-4 | auto | PASS |
| 12 | В `testRuntimeClasspath`/контексте нет `DataSource`; приложение и тесты стартуют без PostgreSQL (БД на машине не запущена) | AC-5 | auto + manual | PASS |
| 13 | `PortInUseTest > startup fails with PortInUseException when the port is taken` | AC-6 | auto | PASS |
| 14 | Второй `./gradlew bootRun --args='--server.port=18090'` при работающем первом → `APPLICATION FAILED TO START … Port 18090 was already in use.`, exit 1 (22 с вместе с Gradle), без зависания | AC-6 | manual | PASS |
| 15 | `ServerPortOverrideTest > application listens on the port given by server.port argument` — `local.server.port` = заданный, health по нему → 200 **(добавлен QA)** | AC-6 (переопределение) | auto | PASS |
| 16 | `SERVER_PORT=18091 java -jar build/libs/auto-statistic-0.0.1-SNAPSHOT.jar` → `Tomcat started on port 18091`, health 200 (переопределение переменной окружения, без изменения кода) | AC-6 | manual | PASS |
| 17 | `grep` по `src/`: нет `org.junit`, `org.assertj`, `org.hamcrest`, `kotlin.test`, `@Test`, `@Autowired`, `lateinit`, `!!`; все спеки — `FunSpec`; `BuildSmokeTest` сохранён; в сборке 10 тестов (≥ 1); нет «SpringExtension on a final class» | AC-7 | auto + manual | PASS |
| 18 | AC-8: временно `["status"] shouldBe "DOWN"` в `HealthEndpointTest` и `shouldNotBeEmpty()` в `AutoStatisticApplicationTest` → `BUILD FAILED`, `HealthEndpointTest > health endpoint returns 200 with status UP FAILED` (`expected:<DOWN> but was:<UP>`), `AutoStatisticApplicationTest > context loads without database FAILED`, «6 tests completed, 2 failed». Правки откатаны, `clean build` зелёный | AC-8 | manual (временная правка) | PASS |

Итого: 18 кейсов — 10 auto (из них 4 добавлены QA), 6 manual, 2 auto + manual.
Итоговый прогон `./gradlew clean build` — **BUILD SUCCESSFUL, 10 тестов, 10 PASSED, 0 skipped/failed**.

Добавленные автотесты (Kotest `FunSpec`, Kotest-матчеры):
- `src/test/kotlin/com/autostatistic/HealthEndpointTest.kt` — 3 теста (кейсы 5, 7, 8);
- `src/test/kotlin/com/autostatistic/ServerPortOverrideTest.kt` — новая спецификация без Spring-расширения (кейс 15).

- Вердикт: **DONE** — AC-1..AC-8 подтверждены.
- Найденные дефекты (шаги, ожидаемо, фактически): нет.
- Наблюдения (не дефекты, на усмотрение architect): `GET /actuator` (discovery) отдаёт `200` со ссылками на `health`. AC это не запрещают,
  внутренние данные не раскрываются. Если discovery не нужен, его можно отключить настройкой `management.endpoints.web.discovery.enabled: false`.

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-23 | product-manager | — → DRAFT | Создана задача: каркас Spring Boot с health-проверкой, AC-1..AC-6; объём зависит от ответа на Q1 |
| 2026-10-23 | architect | DRAFT → PLAN_REVIEW | Технический план: Spring Boot 4.1.2 Web MVC, health = /actuator/health, ProblemDetail для 404, тесты RANDOM_PORT + RestTestClient (ADR-0003); Q1 подтверждён пользователем |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Ревью плана: артефакты/пакеты Boot 4.1.2 сверены с Maven Central (resttestclient, PortInUseException, problemdetails); 4 minor |
| 2026-10-23 | product-manager | READY → DRAFT | Дополнение пользователя «подключить Kotest для тестов» (разработка не начиналась): в описание добавлено требование Kotest; AC-4 уточнён (тесты контекста и HTTP на Kotest, включая AC-3); добавлены AC-7 (все тесты задачи — Kotest-спеки) и AC-8 (негативный: падающий Spring-тест на Kotest роняет build). Технический план требует пересмотра architect |
| 2026-10-23 | architect | DRAFT → PLAN_REVIEW | План пересмотрен под Kotest (ADR-0005): `kotest-extensions-spring` 6.2.6 через `@ApplyExtension(SpringExtension::class)` на каждую Spring-спецификацию, бины через конструктор спеки, `kotlin-spring` открывает `@SpringBootTest`-классы; спецификации `FunSpec`: `AutoStatisticApplicationTest`, `HealthEndpointTest` (3 теста), `PortInUseTest`; проверки — Kotest-матчеры по `exchange().returnResult(...)`, без `expect*`-цепочек; `junit-bom` удалён (JUnit 6.0.3 из BOM Boot = требование `kotest-runner-junit6`); `BuildSmokeTest` остаётся; учтены minor прошлого ревью. Q7 закрыт ответом пользователя |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Ревью плана под Kotest: API Spring 7.0.10, вызываемые `kotest-extensions-spring` 6.2.6, сверены по байткоду; подтверждено, что `@ApplyExtension` даёт конструкторную инъекцию (ConstructorExtension из реестра); `RestTestClient.returnResult` сверен; 2 minor (`shouldNotBeNull()` для `responseBody`, матчер для `Array`) |
| 2026-10-23 | architect | READY (без изменений) | Решение пользователя (2026-10-23, Q8 в feature.md): при неработоспособности `kotest-extensions-spring` на Spring 7 / Boot 4.1.2 — переход на JUnit Jupiter без повторного согласования. В «Риски» вместо «эскалация к architect, JUnit `@Test` запрещён» внесён заранее спроектированный запасной вариант (ADR-0005): триггер с контрольным прогоном, на JUnit — только `@SpringBootTest`-классы, Kotest-матчеры везде, `includeEngines("kotest", "junit-jupiter")`, `@TestConstructor(ALL)`, перечень правок документов, проверка AC-7 в редакции решения пользователя. Основной план и AC не менялись |
| 2026-10-23 | tech-lead | READY (без изменений) | Ревью запасного варианта ADR-0005 (Spring-тесты на JUnit): `@TestConstructor(ALL)` + конструктор Kotlin-класса, `includeEngines("kotest", "junit-jupiter")`, условия срабатывания, имена в отчёте сверены по байткоду spring-test 7.0.10 — корректно. 1 minor (неполный перечень правок conventions.md/architecture.md в ADR-0005) → architect |
| 2026-10-23 | architect | READY (без изменений) | Minor ревью запасного варианта закрыт: перечень «Обязательные правки документов» в ADR-0005 и п.6 «Рисков» дополнен — `conventions.md` «Сборка и зависимости», пункт Spring Boot 4 («в Kotest-спецификации», «примеры на JUnit Jupiter не копировать»); «Тесты», «backtick-функции не используются» (исключение для `@SpringBootTest`-классов); `architecture.md` F-001 — строка «Нет JUnit/AssertJ-тестов» (`includeEngines`) и риск «Транзитивный JUnit Jupiter». Основной план, AC и запасной вариант по существу не менялись |
| 2026-10-23 | kotlin-backend-developer | READY → IN_PROGRESS | Начата реализация по техническому плану (основной путь: Kotest + `kotest-extensions-spring`) |
| 2026-10-23 | kotlin-backend-developer | IN_PROGRESS → IN_REVIEW | Каркас Boot 4.1.2 (webmvc, actuator health, ProblemDetail), 3 Kotest-спеки (`@ApplyExtension(SpringExtension::class)` работает на Spring 7.0.10, запасной вариант не применялся); `./gradlew build` — 6/6 PASSED; AC-8 негатив — BUILD FAILED с именем теста; AC-1/2/3/6 проверены вручную через `bootRun` |
| 2026-10-23 | tech-lead | IN_REVIEW → IN_TESTING | Ревью кода: `clean build` зелёный (6/6 PASSED, без «final class»), основной путь ADR-0005 (Kotest + `@ApplyExtension(SpringExtension)`, конструкторная инъекция) реализован, `exceptionFormat = FULL` сохранён; ручная проверка jar (health 200 UP, 404 problem+json, занятый порт). Замечания разработчика для QA — не дефекты. 0 blocker / 0 major / 3 minor (документация: контракт health с `groups`, строка `Started AutoStatisticApplicationKt` → architect; статус T02 в feature.md исправлен) |
| 2026-10-23 | qa-engineer | IN_TESTING → DONE | 18 кейсов (10 auto, 6 manual, 2 auto+manual); добавлены 4 автотеста (3 в `HealthEndpointTest`, новая `ServerPortOverrideTest`); `clean build` — 10/10 PASSED; AC-8 подтверждён (неверные ожидания в двух Spring-спеках → BUILD FAILED с именами тестов); ручные проверки bootRun/curl на 18090, занятый порт, `SERVER_PORT`. Дефектов нет |
| 2026-10-23 | architect | DONE (без изменений) | Закрыты minor ревью кода (только документация): контракт health — `{"status":"UP"}`, допускается поле `groups` с именами проб, детали не раскрываются (architecture.md «API»/«Потоки», ADR-0003, «API-контракт» T02); ожидаемая строка лога — `Started AutoStatisticApplicationKt` (architecture.md «Потоки», «Как проверить» T02). Наблюдение QA по `GET /actuator`: discovery оставлен включённым (показывает только экспонированный `health`), решение и условия пересмотра — в architecture.md «API». Код не менялся |
