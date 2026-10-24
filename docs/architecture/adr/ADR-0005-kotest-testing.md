# ADR-0005: Тестовый фреймворк — Kotest 6.2 (FunSpec, раннер JUnit 6, kotest-extensions-spring, Kotest-assertions)
- Статус: Accepted
- Дата: 2026-10-23
- Фича: F-001

## Контекст
Пользователь дополнил F-001: «для тестов подключить Kotest». Ответ пользователя на Q7 в F-001 (2026-10-23): Kotest — и раннер/стиль
спецификаций, и основная библиотека проверок; тесты в стиле JUnit Jupiter (`@Test`) не пишутся. Тесты уже в F-001
включают подъём Spring-контекста и HTTP-тесты (`@SpringBootTest(RANDOM_PORT)` + `RestTestClient`, ADR-0003), поэтому
нужна интеграция Kotest со Spring TestContext.

Ограничения стека (ADR-0001..0003): JDK 17, Kotlin 2.3.21, Gradle 9.3.1, Spring Boot 4.1.2 (Spring Framework 7.0.10).
BOM Spring Boot 4.1.2 управляет JUnit **6.0.3** (`junit-jupiter.version`), `kotlinx-coroutines` **1.10.2**, `byte-buddy` **1.18.15**;
Kotest в BOM Boot **не управляется** — версию фиксируем сами.

Проверено по первоисточникам (Maven Central POM/Gradle module metadata, байткод jar, исходники тега `v6.2.6`,
release notes Kotest, javadoc Spring Framework 7.0.10) на 2026-10-23:

| Факт | Значение | Вывод |
|------|----------|-------|
| Последний релиз Kotest | **6.2.6** (14.10.2026), все модули и `io.kotest:kotest-bom` в одной версии | берём 6.2.6 |
| Kotlin, которым собран Kotest 6.2.x | 2.2.21, `languageVersion` 2.2 (минимальный поддерживаемый); release notes 6.1: «Compatible with Kotlin 2.0–2.3» | Kotlin 2.3.21 читает метаданные 2.2 — совместимо |
| Байткод jar Kotest | class file major 55 (Java 11); `jvmMinTarget = 11` | работает на JDK 17 |
| Устройство раннеров Kotest | сам движок (`KotestJunitPlatformTestEngine`) лежит в общем модуле `kotest-runner-junit-platform` 6.2.6 (собран против JUnit Platform 1.13.4) и одинаков для обоих вариантов; `kotest-runner-junit5`/`-junit6` — тонкие модули: `META-INF/services/…TestEngine` + объявление зависимостей JUnit Platform | выбор между ними — это выбор **декларируемой** линии JUnit Platform, а не другого движка; движок в любом случае исполняется на Platform 6.0.3 из BOM Boot |
| `kotest-runner-junit6` 6.2.6 | POM объявляет `junit-platform-engine/launcher/suite-api` и `junit-jupiter-api` **6.0.3**, `kotlinx-coroutines` 1.10.2 | ровно версии BOM Boot 4.1.2 — официально заявленная Kotest пара с Platform 6, дерево зависимостей без принудительных подъёмов; берём |
| `kotest-runner-junit5` 6.2.6 | POM объявляет JUnit Platform 1.13.4 | BOM Boot поднял бы его до 6.0.3 — рабочая конфигурация та же, но с расхождением «объявлено 1.x / исполняется 6.x»; не берём |
| Id JUnit-движка Kotest | `kotest` (`KotestJunitPlatformTestEngine.ENGINE_ID`), регистрация через `META-INF/services` в `kotest-runner-junit6` | `useJUnitPlatform { includeEngines("kotest") }` |
| `io.kotest:kotest-extensions-spring` 6.2.6 | с Kotest 6.0 снова в группе `io.kotest`, версия = версии Kotest; собран против Spring 5.3.39 (runtime-зависимость), byte-buddy 1.18.11 | на classpath Spring поднимается BOM Boot до 7.0.10, byte-buddy — до 1.18.15 |
| API Spring, которые вызывает расширение | `TestContextManager(Class)`, `beforeTestClass/prepareTestInstance/beforeTestMethod/beforeTestExecution/afterTestExecution/afterTestMethod/afterTestClass`, `AutowireCapableBeanFactory.autowire(Class, AUTOWIRE_CONSTRUCTOR, true)` | все присутствуют в Spring 7.0.10 без deprecation — бинарно совместимо |
| Использование на Boot 4 | issue kotest#5666 (Boot 4 + Kotest 6.1.2 + Kotlin 2.3.0) — баг наследования `@ApplyExtension`, исправлен в 6.1.3 | расширение применяется на Boot 4 на практике |
| Gradle 9 | `Test.failOnNoDiscoveredTests` = `true` по умолчанию (с 9.0): есть тестовые исходники, а тестов не найдено → задача `test` падает | «зелёная сборка с 0 тестов» исключена штатно |

## Решение
1. **Kotest 6.2.6**, версия — одна запись `kotest` в `gradle/libs.versions.toml`; модули подключаются без версий через
   `testImplementation(platform(libs.kotest.bom))` (`io.kotest:kotest-bom`) — так же, как BOM Boot (ADR-0002).
2. **Модули** (минимум, YAGNI):
   - `io.kotest:kotest-runner-junit6` — движок Kotest на JUnit Platform 6 (с T01);
   - `io.kotest:kotest-assertions-core` — проверки (`shouldBe`, `shouldNotBeNull`, `shouldThrow…`, коллекции/строки) (с T01);
   - `io.kotest:kotest-extensions-spring` — интеграция со Spring TestContext (с T02).
   Не подключаются: `kotest-property`, `kotest-assertions-json`, MockK, Kotest Gradle-плагин `io.kotest`, KSP — до реальной
   потребности (out of scope F-001).
3. **Запуск — через JUnit Platform** Gradle: `tasks.test { useJUnitPlatform { includeEngines("kotest") } }`.
   Ограничение движком `kotest` делает решение «только Kotest» явным: движок JUnit Jupiter, который приходит транзитивно
   из `spring-boot-starter-test` (через `*-test` стартеры Boot 4), в сборке не исполняется. `junit-platform-launcher` —
   явный `testRuntimeOnly` (как в ADR-0002 п.8). `failOnNoDiscoveredTests` не отключается.
4. **Стиль спецификаций — `FunSpec`** (единый для проекта; другие стили — только новым ADR):
   - тест — `test("<поведение>") { … }`, группировка — `context("<условие/субъект>") { … }`, вложенность ≤ 2 уровней;
   - имя теста — строка, описывающая поведение (как в conventions.md: `"returns NotFound when trip does not exist"`);
     given / when / then — структурой тела (пустые строки/короткие комментарии), а не DSL `BehaviorSpec`;
   - тело спецификации передаётся лямбдой в конструктор: `class XxxTest(deps…) : FunSpec({ … })`
     (без `init { }`), один класс-спецификация на файл, имя класса `<Субъект>Test`, пакет зеркалирует main;
   - проверки — только Kotest-assertions. JUnit/AssertJ/Hamcrest/`kotlin.test` в тестах не используются; Spring-цепочки
     `expectStatus()/expectBody()…` `RestTestClient` не используются как проверки — ответ забирается через
     `exchange().returnResult(…)` и проверяется Kotest-матчерами.
5. **Интеграция со Spring — `io.kotest.extensions.spring.SpringExtension`**, подключается **на каждую Spring-спецификацию**
   аннотацией `@ApplyExtension(SpringExtension::class)` (рядом с `@SpringBootTest`). Глобальная регистрация через
   `AbstractProjectConfig` **не используется**: `SpringExtension.instantiate` создаёт `TestContextManager` для каждой
   спецификации, к которой применён, — глобально это потянуло бы Spring-контекст в чистые unit-спеки.
   - Зависимости бинов — **через конструктор спецификации** (`class HealthEndpointTest(client: RestTestClient)`): расширение
     создаёт экземпляр через `autowire(…, AUTOWIRE_CONSTRUCTOR)`. `@Autowired`-полей и `lateinit` нет (conventions.md).
   - Режим жизненного цикла — по умолчанию (`SpringTestLifecycleMode.Test`, события Spring на каждый leaf-тест).
   - Классы с `@SpringBootTest` открываются плагином `kotlin("plugin.spring")` (all-open знает `@SpringBootTest`) — расширению
     это нужно для генерации «фиктивного» метода теста; предупреждение «SpringExtension on a final class» появиться не должно.
   - Project config Kotest (`io.kotest.provided.ProjectConfig`) в F-001 не создаётся — настроек сверх дефолтов не нужно.
   - Если расширение не работает на Spring 7 / Boot 4.1.2 — применяется предсогласованный «Запасной вариант» (ниже):
     Spring-тесты на JUnit Jupiter с Kotest-матчерами, остальные — Kotest.
6. **Автоматический контроль «только Kotest»** (T03): detekt-правило `style>ForbiddenImport` запрещает импорты
   `org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*` (во всём коде; в main они и так не нужны).
   Вместе с `includeEngines("kotest")` это гарантирует, что JUnit-style тест не появится незамеченным: импорт роняет `detekt`.
   (В запасном варианте `forbiddenImports` не меняется, добавляется белый список `allowedImports: ['org.junit.jupiter.api.Test']` — см. ниже.)

## Последствия
- Плюсы: идиоматичные для Kotlin тесты (строковые имена поведения, лямбда-DSL, корутины из коробки), богатые матчеры;
  версии JUnit Platform у Kotest и Boot совпадают (6.0.3) — один набор платформенных артефактов; единый стиль облегчает ревью.
- Минусы и риски:
  - `kotest-extensions-spring` тестируется командой Kotest на Spring 5.3 / Boot 2.7, а не на Boot 4. Бинарная совместимость
    API подтверждена (см. таблицу), использование на Boot 4 известно. При сбое применяется заранее согласованный
    **запасной вариант** (раздел ниже) — без эскалации и без нового ADR.
  - Kotest не в BOM Boot: обновление Kotest — вручную, сверяя зависимости `kotest-runner-junit6` с `junit-jupiter.version`
    BOM Boot (должны совпадать по major) и поддерживаемые версии Kotlin в release notes Kotest.
  - IntelliJ без плагина Kotest запускает спецификации только через Gradle (`./gradlew test --tests '…'`); плагин «Kotest»
    из Marketplace рекомендован, но не обязателен — сборка и AC от него не зависят.
  - Спецификации-лямбды не видят `this` спецификации в теле до конструирования — зависимости берутся только из параметров
    конструктора (это и есть принятый способ).
  - `conventions.md` (раздел «Тесты», пункт про Boot 4 HTTP-тесты) должен отражать стиль FunSpec — правку вносит tech-lead
    (владелец документа) на ревью плана F-001.

## Запасной вариант: Spring-тесты на JUnit Jupiter
**Решение пользователя (2026-10-23):** Kotest остаётся основным фреймворком. Если `kotest-extensions-spring` реально не
заработает на Spring 7 / Boot 4.1.2, разработчик переходит на этот вариант **без повторного согласования** с пользователем
и без эскалации к architect. Вариант спроектирован заранее; отступать от него (иные обходы) нельзя.

### Когда применяется (триггер)
Все условия одновременно:
1. Spring-спецификация (`@SpringBootTest` + `@ApplyExtension(SpringExtension::class)`) по плану T02 падает или не исполняется
   по причине, лежащей в расширении/его связке со Spring 7, — например: `NoSuchMethodError` / `NoClassDefFoundError` /
   `AbstractMethodError` / `ClassCastException` со стеком в `io.kotest.extensions.spring.*` или в вызовах `TestContextManager`
   из него; спецификация не инстанцируется (нет no-arg конструктора — конструкторная инъекция не сработала); тесты Spring-спеки
   не обнаруживаются/помечаются skipped; намеренно неверная проверка (AC-8 T02) не роняет сборку.
2. Исключены собственные ошибки конфигурации: зависимости по `./gradlew dependencies --configuration testRuntimeClasspath`
   совпадают с планом T02 (Spring 7.0.x, JUnit Platform 6.0.3, byte-buddy 1.18.15, coroutines 1.10.2), `kotlin("plugin.spring")`
   применён, импорты верные (`io.kotest.extensions.spring.SpringExtension`, `io.kotest.core.extensions.ApplyExtension`).
3. **Контрольный прогон:** та же проверка, переписанная по этому разделу на JUnit Jupiter, проходит. Это доказывает, что причина —
   расширение, а не конфигурация приложения. Если и JUnit-вариант падает — причина не в расширении: запасной вариант
   не применяется, проблема решается в рамках T02 обычным порядком.

В «Реализации» T02 (раздел «Отклонения от плана») фиксируются: точная ошибка (класс исключения, первые значимые строки стека),
команда воспроизведения, версии из `dependencies`, результат контрольного прогона.

### Что меняется
| Аспект | Основной план | Запасной вариант |
|--------|---------------|------------------|
| Какие тесты на JUnit Jupiter | никакие | **только Spring-тесты** — классы с `@SpringBootTest` (в F-001: `AutoStatisticApplicationTest`, `HealthEndpointTest`). Тесты без Spring-контекста (`BuildSmokeTest`, `PortInUseTest` и все будущие unit-/доменные тесты) остаются Kotest `FunSpec` |
| Проверки | Kotest-матчеры | **Kotest-матчеры и в JUnit-тестах** (`kotest-assertions-core` не зависит от раннера; падение — `AssertionError` opentest4j, JUnit отчитывает его штатно). `org.junit.jupiter.api.Assertions`/`Assumptions`, AssertJ, Hamcrest, `kotlin.test` — запрещены |
| Зависимости | `kotest-extensions-spring` | `kotest-extensions-spring` **удаляется** из `libs.versions.toml` и `build.gradle.kts`; добавляется `testImplementation(libs.junit.jupiter)` — `org.junit.jupiter:junit-jupiter` без версии (BOM Boot, 6.0.3). Остальное (`kotest-runner-junit6`, `kotest-assertions-core`, `junit-platform-launcher`, `*-test` стартеры, `spring-boot-resttestclient`) — без изменений |
| Gradle | `useJUnitPlatform { includeEngines("kotest") }` | `useJUnitPlatform { includeEngines("kotest", "junit-jupiter") }`; `failOnNoDiscoveredTests` по-прежнему не отключается |
| Интеграция со Spring | `@ApplyExtension(SpringExtension::class)` (Kotest) | штатный JUnit `org.springframework.test.context.junit.jupiter.SpringExtension` — уже мета-аннотирован в `@SpringBootTest`, отдельный `@ExtendWith` не нужен |
| Инъекция бинов | параметры конструктора спецификации | параметры конструктора тест-класса + на классе `@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)` (`org.springframework.test.context.TestConstructor`) — явно на каждом классе, без глобального `junit-platform.properties`; `@Autowired`-полей, `@Autowired` на параметрах и `lateinit` нет |
| Форма теста | `class XTest(dep) : FunSpec({ test("<поведение>") { … } })` | `class XTest(private val dep: …)` с методами ``@Test fun `<поведение>`() { … }`` — имя-поведение в backticks, **тело-блок** (`{ … }`, не expression body: JUnit не исполняет методы с не-`Unit` результатом); группировка `@Nested inner class` не используется (YAGNI); `@DisplayName` не нужен |
| HTTP-проверки | `exchange().returnResult(…)` + Kotest | без изменений: `expect*`-цепочки `RestTestClient` как проверки не используются |
| Имена в отчёте (AC-4/AC-8 T02) | `HealthEndpointTest > health endpoint returns 200 with status UP` | `HealthEndpointTest > health endpoint returns 200 with status UP() FAILED` (JUnit добавляет `()`) |
| detekt `ForbiddenImport` (T03) | `forbiddenImports`: `org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*` | **белый список**: `forbiddenImports` — без изменений; добавляется `allowedImports: ['org.junit.jupiter.api.Test']`. Правило (detekt 2.0.0-alpha.3) сообщает импорт, если его FqName полностью совпал с запрещённым шаблоном и не совпал ни с одним разрешённым — значит, разрешён ровно `org.junit.jupiter.api.Test`, а `Assertions`/`Assumptions`, Kotlin-API Jupiter (`org.junit.jupiter.api.assertThrows`, `assertAll`, `fail` …), `@Disabled`, `@Nested`, `@BeforeEach`, `import org.junit.jupiter.api.*`, JUnit 4 остаются запрещены. Точечный чёрный список отклонён: пропускает Kotlin-проверки JUnit и будущие API Jupiter. Расширение белого списка — правкой этого ADR и conventions.md через ревью. Ограничение «JUnit — только в классах с `@SpringBootTest`» detekt выразить не может (у `ForbiddenImport` нет исключений по файлам) — контролируется ревью кода по чек-листу |

### Обязательные правки документов при срабатывании (в той же задаче T02, разработчиком; conventions.md — проверяет tech-lead на ревью кода)
- **ADR-0005:** в шапку под «Статус» добавить строку `Запасной вариант применён: <дата>, F-001/T02 (ошибка: <кратко>)`;
  статус остаётся `Accepted` (решение предусмотрено этим ADR).
- **`conventions.md`, «Сборка и зависимости»**, пункт Kotest: `includeEngines("kotest", "junit-jupiter")`; `kotest-extensions-spring` не используется;
  `junit-jupiter` — только для Spring-тестов.
- **`conventions.md`, «Сборка и зависимости»**, пункт Spring Boot 4: «HTTP-тесты — `RestTestClient` … в Kotest-спецификации» →
  «… в JUnit-тесте (`@SpringBootTest`-класс, ADR-0005, запасной вариант)»; «примеры на JUnit Jupiter не копировать» → «примеры с JUnit-проверками
  (`Assertions`, `assertThrows`, AssertJ) не копировать; JUnit — только `@Test` в `@SpringBootTest`-классах».
- **`conventions.md`, «Тесты»:**
  - пункт «Фреймворк»: «Kotest — основной; исключение — тесты, поднимающие Spring-контекст (`@SpringBootTest`), пишутся на JUnit Jupiter
    (ADR-0005, запасной вариант) с Kotest-матчерами. Из JUnit разрешён только импорт `org.junit.jupiter.api.Test` (detekt `ForbiddenImport`,
    `allowedImports`); прочие API JUnit, AssertJ, Hamcrest, `kotlin.test` запрещены»;
  - пункт «Стиль спецификаций — `FunSpec`», «backtick-функции не используются» → добавить исключение: «кроме методов ``@Test fun `<поведение>`()``
    в `@SpringBootTest`-классах (запасной вариант ADR-0005)»;
  - пункт «Spring-спецификации» заменить: `@SpringBootTest(...)` + `@TestConstructor(autowireMode = ALL)`, бины — параметрами конструктора,
    методы ``@Test fun `<поведение>`() { … }`` с телом-блоком, класс вручную `open` не делать;
  - пункт «HTTP-тесты» — «в JUnit-тесте» вместо «в Kotest-спецификации», остальное без изменений;
  - удалить требование `@ApplyExtension(SpringExtension::class)` и упоминание предупреждения «SpringExtension on a final class».
- **`conventions.md`, чек-лист:** «только Kotest `FunSpec` и Kotest-матчеры» → «Kotest `FunSpec`; JUnit `@Test` — только в `@SpringBootTest`-классах; проверки — только Kotest-матчеры».
- **`overview.md`**, строка «Тесты» целевого стека — отразить: Spring-тесты на JUnit Jupiter + Kotest-матчеры, без `kotest-extensions-spring`.
- **`architecture.md` F-001**, таблица «Как проверяется», строка «Нет JUnit/AssertJ-тестов»: `includeEngines("kotest")` →
  `includeEngines("kotest", "junit-jupiter")`, проверка — «detekt `ForbiddenImport` (белый список `org.junit.jupiter.api.Test`) + ревью
  (JUnit `@Test` только в `@SpringBootTest`-классах)»; в «Рисках» пункт «Транзитивный JUnit Jupiter» — движок исполняется, но только
  для `@SpringBootTest`-классов.
- **T03:** при реализации взять конфигурацию `ForbiddenImport` из таблицы выше (`forbiddenImports` без изменений + `allowedImports`);
  негативную проверку «только Kotest» выполнять импортами `org.junit.jupiter.api.Assertions.assertEquals`, `org.junit.jupiter.api.assertThrows`
  и `org.assertj.core.api.Assertions.assertThat` вместо `org.junit.jupiter.api.Test`; позитивная — Spring-тесты с `import org.junit.jupiter.api.Test`
  проходят `./gradlew detekt` без находок (план T03, «Риски»).

### Почему именно так
- Только Spring-тесты: сбой касается только расширения; остальные тесты на Kotest работают — пользователь сохранил Kotest основным.
  Перевод всех тестов на JUnit — отклонено (противоречит решению пользователя, без технической причины).
- Kotest-матчеры остаются: единая библиотека проверок во всех тестах, не зависит от раннера.
- Ручной подъём контекста в Kotest-спецификации (`SpringApplication.run`/`TestContextManager` без расширения) — отклонено: теряется
  `@SpringBootTest`-инфраструктура (кеш контекстов, `RANDOM_PORT` + `@AutoConfigureRestTestClient`, `@MockitoBean`), пришлось бы
  поддерживать собственный аналог расширения.
- `@TestConstructor` на классе, а не глобальный `spring.test.constructor.autowire.mode=all`: явно для читателя (по той же причине,
  что отклонена глобальная регистрация `SpringExtension`); `@Autowired` на параметре конструктора в Kotlin с
  `-Xannotation-default-target=param-property` может быть применён и к полю свойства (двойная инъекция) — избегаем.
- Возврат к `kotest-extensions-spring` (когда появится версия, проверенная на Spring 7) — отдельной задачей с обновлением ADR.

## Рассмотренные альтернативы
- **JUnit Jupiter + Kotest-assertions:** как основной вариант отклонено — пользователь подтвердил (Q7): Kotest и как раннер, и как проверки.
  Для Spring-тестов принят как запасной вариант при неработоспособности `kotest-extensions-spring` (решение пользователя 2026-10-23, раздел выше).
- **`kotest-runner-junit5`:** отклонено. Движок у обоих вариантов общий (`kotest-runner-junit-platform`, собран против Platform 1.13.4,
  исполняется на 6.0.3 из BOM Boot в любом случае); различаются только объявленные зависимости: POM `-junit5` декларирует
  Platform 1.13.4 (BOM Boot поднимет до 6.0.3), POM `-junit6` — Platform 6.0.3, ровно как BOM Boot 4.1.2. `-junit6` — официальная
  пара Kotest с Platform 6 и не создаёт расхождения «объявлено/исполняется».
- **Kotest 6.1.11 (последний патч 6.1):** отклонено — 6.2.x актуальна, несёт исправления `SpringExtension` (проброс исключения
  теста в TestContextManager, инвертированный флаг предупреждения), требования к Kotlin/JDK те же.
- **Стили `StringSpec` / `DescribeSpec` / `BehaviorSpec` / `ShouldSpec`:** `StringSpec` не даёт группировки; `BehaviorSpec`/
  `DescribeSpec` добавляют многословный DSL (given/when/then, describe/it) без выигрыша для интеграционных тестов;
  `FunSpec` — минимальный и ближе всего к привычной форме «один тест — одно поведение». Смешение стилей запрещено ради единообразия.
- **Глобальная регистрация `SpringExtension` в `ProjectConfig`:** отклонено — тянет Spring-контекст во все спецификации
  (см. п.5 «Решения»), неявно для читателя теста.
- **Базовый класс `SpringSpec` с `@ApplyExtension`:** отклонено (YAGNI) — 2 Spring-спецификации в F-001; наследование
  `@ApplyExtension` поддерживается с 6.1.3, можно ввести позже без смены решения.
- **Kotest Gradle-плагин `io.kotest`:** не нужен для JVM-проекта (Kotest docs: опционален, даёт лишь улучшения IDE).
- **`kotest-assertions-json` для проверок JSON:** отклонено (YAGNI) — достаточно десериализовать тело в `Map` штатным
  Jackson-конвертером `RestTestClient` и проверить Kotest-матчерами.
