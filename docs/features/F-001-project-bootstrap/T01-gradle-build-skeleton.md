# F-001/T01: Сборка Kotlin-проекта на Gradle

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | DONE                             |
| Фича        | [F-001](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | —                                |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Бутстрап проекта: перевести репозиторий с IntelliJ-модуля без системы сборки на Gradle (Kotlin DSL)
с Gradle Wrapper. После задачи проект собирается и тестируется одной командой из консоли на чистом клоне,
без установленного Gradle и без зависимости от настроек IDE.

Шаблонный `src/Main.kt` («Hello, Kotlin» из мастера IntelliJ) не несёт ценности — его можно удалить или
заменить; артефакты старой конфигурации модуля не должны мешать импорту проекта как Gradle.

Тестовый фреймворк проекта — **Kotest** (дополнение пользователя от 2026-10-23, см. [feature.md](./feature.md)):
в этой задаче Kotest подключается к сборке, и первый (smoke) тест пишется на нём. Стиль спецификаций, модули и
версии Kotest — решение architect.

Spring Boot и проверки качества кода — не в этой задаче (T02, T03).

## Критерии приёмки
- [x] AC-1: Given чистый клон репозитория, установленный JDK требуемой версии и не установленный Gradle,
  When выполнить `./gradlew build` (Windows: `gradlew.bat build`), Then сборка завершается со статусом `BUILD SUCCESSFUL`.
- [x] AC-2: Given в проекте есть хотя бы один тест на Kotest (например, smoke-тест), When выполнить `./gradlew test`
  (и `./gradlew build`), Then тест реально выполняется — виден по имени в выводе Gradle и в отчёте о тестах
  (`build/reports/tests/test/index.html`) — и проходит.
- [x] AC-3 (негативный): Given Kotest-тест, который намеренно падает (проверка Kotest с неверным ожидаемым значением),
  When выполнить `./gradlew build`, Then сборка завершается с ошибкой (`BUILD FAILED`), а в выводе видно имя упавшего
  теста и сообщение о несовпадении — т.е. Kotest-тесты действительно участвуют в сборке. Падающий тест в репозиторий
  не коммитится.
- [x] AC-4 (негативный): Given код с ошибкой компиляции Kotlin, When выполнить `./gradlew build`, Then сборка
  падает с сообщением компилятора.
- [x] AC-5: Given в репозитории закоммичены файлы Gradle Wrapper (скрипты для Unix и Windows, wrapper-jar,
  properties с зафиксированной версией Gradle), When проверить их наличие, Then все они присутствуют и
  скрипт `gradlew` для Unix исполняемый.
- [x] AC-6: Given выполнена сборка, When выполнить `git status`, Then каталоги `build/` и `.gradle/` (и прочие
  артефакты сборки) игнорируются `.gitignore`.
- [x] AC-7: Given проект открыт в IntelliJ IDEA, When выполнить импорт/синхронизацию Gradle, Then проект
  распознаётся как Gradle-проект без ошибок; старый `src/Main.kt` вне стандартной раскладки отсутствует.
  _(QA: подтверждено без GUI — «Тестирование», TC-17..TC-19; импорт в IntelliJ — ручная проверка пользователя, TC-20.)_
- [x] AC-8: Given код задачи в репозитории, When просмотреть тестовые исходники и зависимости сборки, Then тесты написаны
  как Kotest-спецификации с проверками Kotest; тестов в стиле JUnit Jupiter (`@Test`) нет; версия Kotest зафиксирована
  так же, как остальные версии сборки (воспроизводимость).
- [x] AC-9 (граничный): Given в проекте есть Kotest-спецификация со smoke-тестом, When выполнить `./gradlew build`,
  Then в отчёте о тестах число выполненных тестов ≥ 1 — Kotest-спецификации обнаруживаются сборкой, а не пропускаются
  молча («зелёная» сборка с 0 выполненных тестов — дефект).

## Открытые вопросы
- ~~Версия JDK~~ — закрыт (Q5): **JDK 17**, Gradle toolchain 17 ([ADR-0001](../../architecture/adr/ADR-0001-jdk-17.md)).
  «JDK требуемой версии» в AC-1 = JDK 17.
- ~~Базовый пакет/group~~ — закрыт (Q6): `group = com.autostatistic`, пакет `com.autostatistic`
  ([ADR-0002](../../architecture/adr/ADR-0002-gradle-build.md)).
- ~~Q7~~ — закрыт (ответ пользователя 2026-10-23): Kotest — и раннер, и assertions; JUnit-style тесты не пишутся.
- ~~Для architect: «Технический план» устарел относительно AC-2/AC-3/AC-8~~ — пересмотрен под Kotest 2026-10-23 ([ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md)).
- ~~(kotlin-backend-developer → architect / tech-lead)~~ — закрыт tech-lead 2026-10-23 (решение — «Ревью кода», «Решение по AC-9»):
  `failOnEmptyTestSuite` не включаем, правится «Как проверить». Исходный текст вопроса:: проверка плана для AC-9 «пустая `FunSpec({})` → `:test` падает»
  не воспроизводится: Kotest 6.2.6 отдаёт пустую спецификацию в JUnit Platform как один тест-узел (`tests="1"`), сборка зелёная.
  Сценарий AC-9 (спецификации есть, но движком не обнаружены) роняет сборку — подтверждено негативами `includeEngines("kotest-typo")` и
  `excludeEngines("kotest")` (см. «Реализация»). Если «спецификация без тестов» тоже должна валить сборку, нужно решение (например, Kotest
  `failOnEmptyTestSuite` / project config, что сейчас запрещено ADR-0005); иначе — поправить «Как проверить» в плане.

<!-- ============ architect ============ -->
## Технический план
Общая картина и версии — [architecture.md](./architecture.md); решения — ADR-0001, ADR-0002,
[ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md) (Kotest). Пересмотрен 2026-10-23 под Kotest (AC-2, AC-3, AC-8, AC-9).

- **Модули / пакеты:** один Gradle-модуль, корневой проект `auto-statistic`. Создать/изменить файлы:
  1. `settings.gradle.kts`: `rootProject.name = "auto-statistic"`;
     `dependencyResolutionManagement { repositories { mavenCentral() } }` (репозитории только здесь, в `build.gradle.kts` блока `repositories` нет).
  2. `gradle/libs.versions.toml`:
     - `[versions]`: `kotlin = "2.3.21"`, `junit = "6.0.3"`, `kotest = "6.2.6"`;
     - `[libraries]`:
       - `junit-bom` (`org.junit:junit-bom`, version.ref junit), `junit-platform-launcher` (`org.junit.platform:junit-platform-launcher`, без версии);
       - `kotest-bom` (`io.kotest:kotest-bom`, version.ref kotest), `kotest-runner-junit6` (`io.kotest:kotest-runner-junit6`, без версии),
         `kotest-assertions-core` (`io.kotest:kotest-assertions-core`, без версии);
       - `junit-jupiter` **не объявлять** (тесты не на Jupiter; ADR-0005).
     - `[plugins]`: `kotlin-jvm` (`org.jetbrains.kotlin.jvm`, version.ref kotlin).
     Литеральных версий в `build.gradle.kts` нет. Координаты Kotest — без суффикса `-jvm` (Gradle выберет JVM-вариант по module metadata).
  3. `build.gradle.kts`:
     - `plugins { alias(libs.plugins.kotlin.jvm) }`; `group = "com.autostatistic"`, `version = "0.0.1-SNAPSHOT"`;
     - `kotlin { jvmToolchain(17) }`;
     - зависимости:
       `testImplementation(platform(libs.junit.bom))`, `testImplementation(platform(libs.kotest.bom))`,
       `testImplementation(libs.kotest.runner.junit6)`, `testImplementation(libs.kotest.assertions.core)`,
       `testRuntimeOnly(libs.junit.platform.launcher)` (в Gradle 9 launcher объявляется явно; `junit-bom` нужен, чтобы у launcher была
       версия до появления BOM Boot в T02 — 6.0.3, та же, что требует `kotest-runner-junit6` 6.2.6);
     - ```kotlin
       import org.gradle.api.tasks.testing.logging.TestExceptionFormat // в начале файла

       tasks.test {
           useJUnitPlatform { includeEngines("kotest") }
           testLogging {
               events("passed", "skipped", "failed")
               exceptionFormat = TestExceptionFormat.FULL
           }
       }
       ```
       `exceptionFormat = TestExceptionFormat.FULL` — чтобы в выводе Gradle был текст несовпадения `expected:<…> but was:<…>` (AC-3);
       с дефолтным `SHORT` выводится только `AssertionFailedError at BuildSmokeTest.kt:8` (выявлено при реализации, принято ревью кода).
       `includeEngines("kotest")` — исполняется только движок Kotest (id `kotest`). `failOnNoDiscoveredTests` **не трогать**: в Gradle 9
       по умолчанию `true` — при наличии тестовых исходников и 0 обнаруженных тестов задача `test` падает (AC-9). `testLogging` даёт имена
       тестов в консоли (`BuildSmokeTest > tests run on the JDK 17 toolchain PASSED`), отчёт — `build/reports/tests/test/index.html` (AC-2).
     - Kotest Gradle-плагин `io.kotest`, KSP, project config Kotest — **не** подключать (ADR-0005).
  4. `gradle.properties`: `org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8`, `kotlin.code.style=official`.
     Configuration cache / parallel пока не включать.
  5. Gradle Wrapper **9.3.1**: Gradle не установлен — разово скачать `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`
     во временный каталог **вне репозитория** и выполнить из корня проекта
     `<tmp>/gradle-9.3.1/bin/gradle wrapper --gradle-version 9.3.1 --distribution-type bin --gradle-distribution-sha256-sum <sha256>`
     (sha256 — с `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip.sha256`; сверено tech-lead:
     `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`). Альтернатива — сгенерировать через IntelliJ, затем вручную добавить
     `distributionSha256Sum`. Результат: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
     `gradle/wrapper/gradle-wrapper.properties` (с `distributionUrl` 9.3.1-bin и `distributionSha256Sum`).
  6. `.gitattributes`: `* text=auto eol=lf`, `*.bat text eol=crlf`, `*.jar binary` (LF согласовано с `.editorconfig` из T03).
     После `git add`: `git update-index --chmod=+x gradlew` (AC-5; проверка `git ls-files -s gradlew` → `100755`).
  7. `.gitignore`: добавить `build/`, `!gradle/wrapper/gradle-wrapper.jar` (на случай глобальных правил),
     `.gradle/`, `*.iml`, `.idea/`; оставить `out/`, `.kotlin`.
  8. Удалить `src/Main.kt`, `auto-statistic.iml`, `.idea/libraries/KotlinJavaRuntime.xml`, `.idea/modules.xml` (AC-7; `.idea` IDE пересоздаст
     при импорте Gradle: File → Open → `build.gradle.kts` → Open as Project / «Link Gradle project»).
  9. Тест — Kotest-спецификация в стиле **`FunSpec`** (ADR-0005), `src/test/kotlin/com/autostatistic/BuildSmokeTest.kt`, пакет `com.autostatistic`:
     ```kotlin
     class BuildSmokeTest : FunSpec({
         test("tests run on the JDK 17 toolchain") {
             Runtime.version().feature() shouldBe 17
         }
     })
     ```
     (форма — ориентир; суть: один осмысленный по имени тест, проверка — Kotest-матчер `io.kotest.matchers.shouldBe`; заодно подтверждает,
     что тесты исполняются на JDK из toolchain). Никаких `org.junit.*` / `@Test` / `kotlin.test` (AC-8). `src/main/kotlin` в T01 может оставаться
     пустым (класс приложения появится в T02). Спецификация **остаётся** после T02.
- **Стиль тестов (для всех задач, ADR-0005):** `FunSpec`, тело — лямбдой в конструктор (`class XxxTest : FunSpec({ … })`), имя теста —
  строка-поведение, группировка `context("…")` при необходимости, один класс на файл, имя класса `<Субъект>Test`.
- **API-контракт:** нет.
- **Модель данных / миграции:** нет.
- **Доменные типы и ошибки:** нет.
- **Ключевые решения:** JDK 17 toolchain — ADR-0001; Gradle 9.3.1 + Kotlin 2.3.21 + catalog + один модуль + координаты — ADR-0002;
  Kotest 6.2.6 (`kotest-runner-junit6` + `kotest-assertions-core`, BOM, только движок `kotest`, стиль `FunSpec`) — ADR-0005.
  Совместимость: Kotest 6.2.6 собран Kotlin 2.2.21 (читается 2.3.21), байткод Java 11, требует JUnit Platform 6.0.3 = `junit-bom` T01 = BOM Boot T02.
- **Как проверить:**
  - AC-1/AC-2/AC-9 — `./gradlew build` и `gradlew.bat build` на JDK 17: в выводе `BuildSmokeTest > tests run on the JDK 17 toolchain PASSED`,
    в `build/reports/tests/test/index.html` — 1 тест, 0 failures; в `build/test-results/test/TEST-com.autostatistic.BuildSmokeTest.xml` `tests="1"`.
  - AC-9 (негатив, не коммитить): в `build.gradle.kts` временно `useJUnitPlatform { includeEngines("kotest"); excludeEngines("kotest") }` →
    `./gradlew build` падает: `:test FAILED`, «There are test sources present and no filters are applied, but the test task did not discover
    any tests to execute…» (Gradle 9 `failOnNoDiscoveredTests`), `BUILD FAILED`. Дополнительно: `includeEngines("kotest-typo")` → `:test FAILED`
    (`JUnitException: No TestEngine ID matched…` — падает JUnit Platform ещё до `failOnNoDiscoveredTests`), `BUILD FAILED`.
    Пустая спецификация `FunSpec({})` негативом **не является**: Kotest отдаёт её в JUnit Platform как один тест (`tests="1"`), сборка зелёная;
    это не сценарий AC-9 (спецификация обнаружена), `failOnNoDiscoveredTests` его не покрывает — ловится ревью (`failOnEmptyTestSuite` не включаем, ADR-0005).
  - AC-3 (не коммитить): `Runtime.version().feature() shouldBe 18` → `BUILD FAILED`, в выводе `BuildSmokeTest > tests run on the JDK 17 toolchain FAILED`
    и сообщение о несовпадении `expected:<18> but was:<17>`.
  - AC-4 — синтаксическая ошибка в тесте (не коммитить) → `:compileTestKotlin FAILED` с `e: file:///…:строка:колонка`.
  - AC-5 — `git ls-files -s gradlew gradlew.bat gradle/wrapper/*`; AC-6 — `git status --ignored` после сборки; AC-7 — импорт в IntelliJ (manual).
  - AC-8 — ревью: в тестах нет импортов `org.junit` / `kotlin.test` / `org.assertj`, версия `kotest` — в каталоге.
    Начиная с T03 — автоматически, detekt `ForbiddenImport`.
- **Риски:** генерация wrapper без Gradle (п.5); x-бит `gradlew` на Windows (п.6); если `JAVA_HOME` указывает не на 17 —
  не страшно, toolchain найдёт JDK 17 в `~/.jdks`; если JDK 17 не найден — понятная ошибка «No matching toolchains found».
  Запуск спецификаций кнопкой в IntelliJ без плагина «Kotest» может быть недоступен — запускать через Gradle
  (`./gradlew test --tests 'com.autostatistic.BuildSmokeTest'`) или поставить плагин Kotest из Marketplace (рекомендация, на AC не влияет).

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Проверено (2026-10-23, по первоисточникам):
  - Декомпозиция: срез «сборка + тесты в сборке» самостоятелен и проверяем, размер S, зависимостей нет.
  - AC-1..AC-7 проверяемы, есть негативные (AC-3, AC-4); способ проверки каждого AC указан в плане.
  - Версии: Gradle 9.3.1 существует (sha256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`);
    KGP 2.3.21 официально поддерживает Gradle 7.6.3–9.3.0 (9.3.1 — патч той же линии, допустимо); JUnit 6.0.3 совпадает с
    `junit-jupiter.version` в BOM Spring Boot 4.1.2 — переход на BOM в T02 не меняет версию. Gradle 9 запускается на JDK 17 (локально 17.0.11).
  - План согласован с ADR-0001/ADR-0002 и conventions.md (версии только в каталоге, репозитории в settings, без foojay).
- Замечания (не блокируют; учесть при реализации, отклонение от плана описать в «Реализации»):
  - [minor] п.8: `.idea/modules.xml` ссылается на удаляемый `auto-statistic.iml` — при открытии IDE покажет ошибку загрузки модуля
    до Gradle-импорта. → Удалить также `.idea/modules.xml` (или весь `.idea/` локально перед импортом — он всё равно игнорируется), AC-7.
  - [minor] п.6: в git включён `core.autocrlf=true`, а `.editorconfig` (T03) требует `end_of_line = lf`. При `* text=auto` рабочие копии
    `.kt/.kts` на Windows будут CRLF, `ktlintFormat` начнёт переписывать окончания строк (шумные диффы). → В `.gitattributes`
    использовать `* text=auto eol=lf` (оставив `*.bat text eol=crlf`, `*.jar binary`) — согласовано с `.editorconfig`.
  - [minor] п.5: порядок — сначала сгенерировать wrapper (до появления `build.gradle.kts` или с уже готовыми файлами — оба варианта
    работают, т.к. `wrapper` конфигурирует проект и скачает KGP), но zip дистрибутива и распакованный Gradle держать вне репозитория.

### Ревью плана под Kotest (2026-10-23)
- Вердикт: **READY**
- Проверено по артефактам Maven Central (POM / Gradle module metadata / байткод jar Kotest 6.2.6):
  - `io.kotest:kotest-bom:6.2.6` существует и содержит `kotest-runner-junit6`, `kotest-assertions-core`, `kotest-extensions-spring`
    (+ `-jvm`-варианты) — подключение без версий через `platform(libs.kotest.bom)` корректно, координаты без `-jvm` резолвятся по metadata.
  - `kotest-runner-junit6` 6.2.6 тянет `junit-platform-engine/launcher/suite-api` и `junit-jupiter-api` **6.0.3**, coroutines 1.10.2,
    `kotlin-stdlib` 2.2.21 (поднимется KGP до 2.3.21), `kotlin-reflect` 2.2.21 (runtime) — с `junit-bom` 6.0.3 в T01 и BOM Boot в T02 конфликтов нет.
  - Id движка — `kotest` (`KotestJunitPlatformTestEngine.ENGINE_ID`), регистрация через `META-INF/services/org.junit.platform.engine.TestEngine`
    в `kotest-runner-junit6` — `includeEngines("kotest")` корректен.
  - AC-1..AC-9 покрыты: AC-2/AC-9 — `testLogging` + отчёт + `failOnNoDiscoveredTests` (дефолт Gradle 9); AC-3 — падающий `shouldBe`;
    AC-8 — ревью, с T03 — detekt `ForbiddenImport`. Стиль `FunSpec` и конвенции согласованы с ADR-0005; conventions.md («Тесты»,
    «Сборка и зависимости») обновлён tech-lead под Kotest.
- Замечания (не блокируют; учесть при реализации / актуализации документов):
  - [minor] → architect, ADR-0005 (таблица фактов, «Рассмотренные альтернативы») и architecture.md: обоснование «`kotest-runner-junit5`
    собран против Platform 1.13.4 — бинарная совместимость не гарантирована; `-junit6` совпадает с BOM точно» неточно. `kotest-runner-junit6-jvm.jar`
    содержит **только** `META-INF/services/...TestEngine`; сам движок лежит в общем `kotest-runner-junit-platform` 6.2.6, который собран против
    JUnit Platform **1.13.4** и в обоих вариантах исполняется на 6.0.3. Выбор `-junit6` верен (это официально заявленная Kotest пара с Platform 6:
    POM декларирует 6.0.3), но формулировку стоит исправить: «junit6 — модуль-выравнивание зависимостей Kotest под Platform 6; движок общий».
    Риск для реализации не меняется: если `:test` упадёт с `NoSuchMethodError`/`NoClassDefFoundError` из `org.junit.platform.*` — фиксировать в «Реализации» и эскалировать.
  - [minor] «Как проверить», AC-9 (негатив): пустой `FunSpec({})` проверяет «0 тестов», но не сценарий «спецификации есть, но не обнаружены движком»,
    ради которого AC-9 и написан. → Дополнительно (не коммитить): временно `includeEngines("kotest-typo")` → `:test` падает
    («There are test sources present … but the test task did not discover any tests»). Это прямо доказывает, что 0 обнаруженных тестов роняет сборку.

<!-- ============ kotlin-backend-developer ============ -->
## Реализация
- **Изменённые файлы:**
  - созданы: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitattributes`,
    `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`,
    `src/test/kotlin/com/autostatistic/BuildSmokeTest.kt`;
  - изменён: `.gitignore` (добавлены `.gradle/`, `build/`, `!gradle/wrapper/gradle-wrapper.jar`, `*.iml`, `.idea/`; `out/`, `.kotlin` сохранены);
  - удалены: `src/Main.kt`, `auto-statistic.iml`, `.idea/libraries/KotlinJavaRuntime.xml`, `.idea/modules.xml` (minor ревью плана учтён).
- **Что сделано:**
  - Сборка по п.1–4 плана: репозитории только в `settings.gradle.kts`; версии (`kotlin 2.3.21`, `junit 6.0.3`, `kotest 6.2.6`) только в каталоге,
    модули Kotest/launcher без версий через `platform(...)`; `jvmToolchain(17)`; `useJUnitPlatform { includeEngines("kotest") }`,
    `failOnNoDiscoveredTests` не трогался; Kotest-плагин/KSP/ProjectConfig не подключались; `src/main/kotlin` пуст (класс приложения — T02).
  - Wrapper 9.3.1 сгенерирован дистрибутивом Gradle 9.3.1, скачанным во временный каталог вне репозитория (sha256 zip сверен:
    `b266d5ff…beaff06`); `gradle-wrapper.properties` содержит `distributionUrl` 9.3.1-bin и `distributionSha256Sum`.
  - `.gitattributes`: `* text=auto eol=lf`, `*.bat text eol=crlf`, `*.jar binary` (minor ревью плана учтён).
  - x-бит (AC-5): `git add --chmod=+x gradlew` — **в индекс добавлен только `gradlew`** (коммитов нет);
    `git ls-files -s gradlew` → `100755`. При последующем `git add` режим сохранится (`core.filemode=false`).
- **Тесты:** `BuildSmokeTest` (Kotest `FunSpec`, `test("tests run on the JDK 17 toolchain")`, матчер `shouldBe`).
  Запуск: `./gradlew build` / `gradlew.bat build`; одна спецификация — `./gradlew test --tests 'com.autostatistic.BuildSmokeTest'`.
  Результаты проверок (JDK 17.0.11, Windows 11, 2026-10-23):
  - AC-1: `./gradlew build` (Git Bash) и `gradlew.bat clean build` (PowerShell) — `BUILD SUCCESSFUL`. Дополнительно — «чистый клон»:
    неигнорируемые файлы проекта скопированы во временный каталог, там `./gradlew build` — `BUILD SUCCESSFUL`.
  - AC-2 / AC-9: в выводе `BuildSmokeTest > tests run on the JDK 17 toolchain PASSED`; `build/reports/tests/test/index.html` — 1 tests,
    0 failures, 0 skipped, 100% successful; `TEST-com.autostatistic.BuildSmokeTest.xml` — `tests="1"`.
  - AC-3 (не закоммичено): `shouldBe 18` → `BUILD FAILED`, в выводе `BuildSmokeTest > tests run on the JDK 17 toolchain FAILED` и
    `org.opentest4j.AssertionFailedError: expected:<18> but was:<17>`.
  - AC-4 (не закоммичено): синтаксическая ошибка → `:compileTestKotlin FAILED`,
    `e: file:///…/BuildSmokeTest.kt:8:50 Syntax error: Expecting an element.`, `BUILD FAILED`.
  - AC-9, негатив `includeEngines("kotest-typo")` (minor ревью, не закоммичено): `:test FAILED`, `BUILD FAILED`; причина —
    `JUnitException: No TestEngine ID matched the following include EngineFilters: [kotest-typo]` (падает JUnit Platform ещё до
    `failOnNoDiscoveredTests`, текст «did not discover any tests» не выводится).
  - AC-9, ветка `failOnNoDiscoveredTests` (доп., не закоммичено): `includeEngines("kotest"); excludeEngines("kotest")` → `:test FAILED`,
    «There are test sources present and no filters are applied, but the test task did not discover any tests to execute…».
  - AC-9, негатив из плана «пустая `FunSpec({})`»: **сборка зелёная** — Kotest регистрирует пустую спецификацию как один тест-узел
    (`TEST-Gradle-Test-Run--test.xml`: `tests="1"`, testcase `BuildSmokeTest`), поэтому Gradle не считает, что тестов 0. См. «Открытые вопросы».
  - AC-5: `gradlew` (100755 в индексе), `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` (не игнорируется — `!gradle/wrapper/gradle-wrapper.jar`),
    `gradle/wrapper/gradle-wrapper.properties` присутствуют.
  - AC-6: после сборки `git status --short --ignored` → `!! .gradle/`, `!! build/`, `!! .idea/`; в `??` артефактов сборки нет.
  - AC-7: manual (импорт в IntelliJ) — не выполнялся агентом; `src/Main.kt`, `*.iml`, `.idea/modules.xml` удалены, проект — стандартная раскладка Gradle.
  - AC-8: тест — Kotest `FunSpec` + `io.kotest.matchers.shouldBe`; импортов `org.junit` / `kotlin.test` / `org.assertj` нет; `junit-jupiter`
    в каталоге не объявлен; версия `kotest` — в `gradle/libs.versions.toml`.
- **Отклонения от плана:**
  - `build.gradle.kts`: в `testLogging` добавлен `exceptionFormat = TestExceptionFormat.FULL` (+ `import org.gradle.api.tasks.testing.logging.TestExceptionFormat`).
    Причина: с форматом по умолчанию (`SHORT`) в выводе Gradle было только `org.opentest4j.AssertionFailedError at BuildSmokeTest.kt:8`
    без текста несовпадения, а AC-3 требует сообщение о несовпадении в выводе сборки.
  - `.idea/` целиком добавлен в `.gitignore` (план п.7) — оставшиеся локальные файлы IDE (`misc.xml`, `kotlinc.xml`, `workspace.xml` и др.)
    не удалялись, они игнорируются и будут перезаписаны импортом Gradle.

<!-- ============ tech-lead ============ -->
## Ревью кода
- Вердикт: **IN_TESTING** (круг 0; blocker 0, major 0, minor 2)
- Проверено (2026-10-23, JDK 17.0.11, Windows 11):
  - `./gradlew clean build` — `BUILD SUCCESSFUL`, в выводе `BuildSmokeTest > tests run on the JDK 17 toolchain PASSED`,
    `TEST-com.autostatistic.BuildSmokeTest.xml`: `tests="1" failures="0" errors="0"`.
  - Соответствие плану п.1–9: репозитории только в `settings.gradle.kts`; версии только в `gradle/libs.versions.toml`, модули без версий через
    `platform(...)`; `junit-jupiter` не объявлен; `includeEngines("kotest")`, `failOnNoDiscoveredTests` не тронут; Kotest-плагина/KSP/ProjectConfig нет.
  - Wrapper: `distributionUrl` 9.3.1-bin + `distributionSha256Sum` = сверенный `b266d5ff…beaff06`; `gradle-wrapper.jar` — zip, не игнорируется;
    `gradlew` — LF и `100755` в индексе, `gradlew.bat` — CRLF; `.gitattributes` по плану (`eol=lf`).
  - `.gitignore`: `git check-ignore` подтверждает `build/`, `.gradle/`, `.idea/`, `.kotlin`; `src/Main.kt`, `*.iml`, `.idea/modules.xml` удалены.
  - Тест: Kotest `FunSpec`, тело лямбдой, имя-поведение, матчер `shouldBe`, пакет `com.autostatistic`; импортов `org.junit`/`kotlin.test`/`org.assertj` нет.
  - Негатив «пустая `FunSpec({})`» воспроизведён tech-lead во временной копии проекта: сборка зелёная, `TEST-Gradle-Test-Run--test.xml`
    `tests="1"`, testcase `BuildSmokeTest` — наблюдение разработчика подтверждено.
- **Решение по AC-9 (открытый вопрос разработчика):**
  - AC-9 требует, чтобы спецификации **обнаруживались** сборкой и «зелёная» сборка с 0 выполненных тестов была невозможна. Пустая `FunSpec({})`
    обнаруживается (Kotest отдаёт спецификацию в JUnit Platform как узел-тест) — это не сценарий AC-9, а ошибка автора спецификации,
    которая ловится ревью. Сценарий AC-9 («спецификации есть, движок их не нашёл») роняет сборку — подтверждено `excludeEngines("kotest")`
    (ветка `failOnNoDiscoveredTests`) и `includeEngines("kotest-typo")`. AC-9 реализован.
  - `failOnEmptyTestSuite` / project config Kotest **не включаем**: противоречит ADR-0005 (project config в F-001 не создаётся), а выигрыш —
    только защита от пустых спецификаций, что закрывается ревью. При необходимости — отдельным ADR.
  - Неверна проверка в «Как проверить» (AC-9, негатив) — дефект плана, не кода. Канонический негатив AC-9 для QA (не коммитить):
    `useJUnitPlatform { includeEngines("kotest"); excludeEngines("kotest") }` → `:test FAILED`, «There are test sources present … but the test task
    did not discover any tests…», `BUILD FAILED`; дополнительно `includeEngines("kotest-typo")` → `:test FAILED` (`No TestEngine ID matched…`).
- Замечания:
  - [minor] → architect: T01 «Технический план» → «Как проверить», AC-9 (негатив), и architecture.md (таблица проверок, строка «0 тестов»):
    заменить «пустая `FunSpec({})` → падает» на канонический негатив выше и дописать, что пустая спецификация Kotest считается одним тестом
    (не покрывается `failOnNoDiscoveredTests`, ловится ревью). Не блокирует T01: QA использует негатив из этого раздела.
  - [minor] `build.gradle.kts:1,27` — отклонение `exceptionFormat = TestExceptionFormat.FULL` **принято**: без него в выводе Gradle нет текста
    несовпадения, а AC-3 его требует; литералов версий и нарушений conventions нет. → architect: отразить в плане T01 п.3 / architecture.md
    (эталонный `tasks.test`), чтобы при правках `build.gradle.kts` в T02/T03 настройка не потерялась.
- Для QA: AC-7 (импорт в IntelliJ) разработчиком не проверялся — проверить вручную.

<!-- ============ qa-engineer ============ -->
## Тестирование
Окружение: Windows 11, JDK 17.0.11 (Liberica, `JAVA_HOME`), Gradle не установлен (`which gradle` — пусто), 2026-10-23.
«Чистый клон» (TC-01, TC-02): неигнорируемые файлы проекта (без `docs/`, `.claude/`, `CLAUDE.md`) закоммичены во временный git-репозиторий
вне проекта и склонированы `git clone` (применяются `.gitattributes`/`core.autocrlf`); сборка — с **пустым** `GRADLE_USER_HOME` (wrapper сам
скачал дистрибутив 9.3.1 с проверкой `distributionSha256Sum`). Негативы TC-06..TC-13 выполнены в этом клоне (рабочая копия проекта не менялась),
после каждого сценария клон откатывался `git checkout -- . && git clean -fd src`.

| Кейс | Проверяет AC | Тип (auto/manual) | Результат |
|------|--------------|-------------------|-----------|
| TC-01 Чистый клон, пустой `GRADLE_USER_HOME`, `./gradlew build` (Git Bash) → wrapper скачал `gradle-9.3.1-bin`, `BUILD SUCCESSFUL` | AC-1, AC-5 | auto (скрипт) | PASS |
| TC-02 Чистый клон, `gradlew.bat clean build` (PowerShell) → `BUILD SUCCESSFUL` | AC-1 | auto (скрипт) | PASS |
| TC-03 Рабочая копия проекта, `./gradlew clean build` → `BUILD SUCCESSFUL` (и финальный прогон после всех проверок) | AC-1 | auto (`./gradlew build`) | PASS |
| TC-04 Вывод: `BuildSmokeTest > tests run on the JDK 17 toolchain PASSED`; `index.html`: 1 tests / 0 failures / 0 ignored, имя `BuildSmokeTest` в отчёте; `TEST-com.autostatistic.BuildSmokeTest.xml`: `tests="1" failures="0" errors="0"` | AC-2, AC-9 | auto (`BuildSmokeTest`) | PASS |
| TC-05 `./gradlew clean test` и `./gradlew test --tests com.autostatistic.BuildSmokeTest` → тест выполнен и виден по имени, `BUILD SUCCESSFUL` | AC-2 | auto (скрипт) | PASS |
| TC-06 Граничный: вторая спецификация в другом пакете (`context` + 2 теста) → обнаружена, `SecondSpecTest > group > a/b PASSED`, итого 3 теста | AC-9 | auto (скрипт, не коммитилось) | PASS |
| TC-07 Негатив: `shouldBe 18` → `BuildSmokeTest > tests run on the JDK 17 toolchain FAILED`, `AssertionFailedError: expected:<18> but was:<17>`, `:test FAILED`, `BUILD FAILED` | AC-3 | auto (скрипт, не коммитилось) | PASS |
| TC-08 Негатив: `shouldBe "17"` → `expected:kotlin.String<17> but was:kotlin.Int<17>`, `BUILD FAILED` | AC-3 | auto (скрипт, не коммитилось) | PASS |
| TC-09 Негатив: синтаксическая ошибка в тесте → `:compileTestKotlin FAILED`, `e: file:///…/BuildSmokeTest.kt:8:50 Syntax error: Expecting an element.`, `BUILD FAILED` | AC-4 | auto (скрипт, не коммитилось) | PASS |
| TC-10 Негатив: `src/main/kotlin/…/Broken.kt` с неразрешённой ссылкой → `:compileKotlin FAILED`, `e: file:///…/Broken.kt:3:21 Unresolved reference 'undefinedSymbol'.`, `BUILD FAILED` | AC-4 | auto (скрипт, не коммитилось) | PASS |
| TC-11 Негатив AC-9 (канонический, из «Ревью кода»): `includeEngines("kotest"); excludeEngines("kotest")` → `:test FAILED`, «There are test sources present and no filters are applied, but the test task did not discover any tests to execute…», `BUILD FAILED` | AC-9 | auto (скрипт, не коммитилось) | PASS |
| TC-12 Негатив AC-9 (доп.): `includeEngines("kotest-typo")` → `:test FAILED` («Test process encountered an unexpected problem»), `BUILD FAILED` | AC-9 | auto (скрипт, не коммитилось) | PASS |
| TC-13 Наблюдение: класс с JUnit `@Test` (падающий) рядом со smoke → компилируется (`junit-jupiter-api` транзитивно от `kotest-runner-junit6`), движком `kotest` не исполняется, сборка зелёная (`tests="1"`). Не дефект T01 (такого кода нет; AC-8 — ревью), см. «Замечания» | AC-8 | auto (скрипт, не коммитилось) | INFO |
| TC-14 Wrapper: `gradlew` (`100755` в индексе, LF, `#!/bin/sh`), `gradlew.bat` (CRLF), `gradle-wrapper.jar`, `gradle-wrapper.properties` (`distributionUrl` 9.3.1-bin, `distributionSha256Sum` = `b266d5ff…beaff06`); в клоне режим `gradlew` `100755` сохранён | AC-5 | auto (git) | PASS |
| TC-15 `gradle-wrapper.jar` sha256 = официальному `gradle-9.3.1-wrapper.jar.sha256` (`b3a875dd…a7c42ec13`); jar не игнорируется (`!gradle/wrapper/gradle-wrapper.jar`) | AC-5 | auto (sha256sum + curl) | PASS |
| TC-16 После сборки `git status --short --ignored`: проект — `!! .gradle/`, `!! build/`, `!! .idea/`; клон — `!! .gradle/`, `!! build/`; артефактов сборки в `??` нет; `git check-ignore` — `build/`, `.gradle/`, `.idea/`, `.kotlin`, `*.iml`, `out/` | AC-6 | auto (git) | PASS |
| TC-17 `./gradlew projects` → `Root project 'auto-statistic'`, `No sub-projects` — проект определяется Gradle корректно | AC-7 | auto (Gradle) | PASS |
| TC-18 `src/Main.kt`, `*.iml`, `.idea/modules.xml`, `.idea/libraries/` отсутствуют; среди отслеживаемых/неигнорируемых нет `*.iml`, `.idea/`, `Main.kt`, `out/`; в локальном `.idea/` нет ссылок на `KotlinJavaRuntime` / `auto-statistic.iml` | AC-7 | auto (fs/git/grep) | PASS |
| TC-19 Раскладка исходников стандартная Gradle: только `src/test/kotlin/com/autostatistic/BuildSmokeTest.kt` | AC-7 | auto (fs) | PASS |
| TC-20 IntelliJ: File → Open → `build.gradle.kts` → Open as Project, Gradle sync без ошибок, SDK проекта 17 | AC-7 | **manual (пользователь)** | не выполнялся агентом (нет GUI) — ручная проверка пользователя |
| TC-21 Ревью тестов и сборки: `FunSpec` + `io.kotest.matchers.shouldBe`; импортов `org.junit` / `kotlin.test` / `org.assertj` и `@Test` нет; `junit-jupiter` не объявлен; `kotest = "6.2.6"` в `gradle/libs.versions.toml`, литеральных версий в `build.gradle.kts` нет | AC-8 | manual (ревью) | PASS |

- Автотесты: новых спецификаций не добавлено. Задача — инфраструктура сборки, кода приложения нет; её AC проверяются на уровне сборки
  (вывод Gradle, отчёты, git), негативы по определению не коммитятся (AC-3), а лишние спецификации изменили бы зафиксированную в плане/ревью
  базу «1 тест». Покрытие: `BuildSmokeTest` (позитив AC-2/AC-9) + скриптовые сценарии TC-01..TC-19.
- Вердикт: **DONE** — AC-1..AC-9 подтверждены, дефектов нет. AC-7: автоматизируемая часть подтверждена (TC-17..TC-19), импорт в GUI IntelliJ
  (TC-20) — ручная проверка пользователя, дефектом не является.
- Найденные дефекты: нет.
- Замечания (не дефекты T01):
  - [info] → T03 (detekt `ForbiddenImport`): `org.junit.jupiter.api.*` есть на тестовом classpath транзитивно (`kotest-runner-junit6`), поэтому
    JUnit-тест с `@Test` компилируется и **молча не исполняется** (движок `kotest` его не видит, `failOnNoDiscoveredTests` не срабатывает,
    пока есть Kotest-спеки) — TC-13. До T03 защита только ревью; при приёмке T03 проверить негатив с `import org.junit.jupiter.api.Test`
    в классе без `@SpringBootTest`.
  - [info] Пустая `FunSpec({})` даёт зелёную сборку (Kotest считает её одним тестом) — известно, решено tech-lead («Решение по AC-9»).

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-23 | product-manager | — → DRAFT | Создана задача: бутстрап Gradle-сборки, критерии приёмки AC-1..AC-7 |
| 2026-10-23 | architect | DRAFT → PLAN_REVIEW | Технический план: Gradle 9.3.1 wrapper, Kotlin 2.3.21, toolchain 17, version catalog, group/пакет com.autostatistic; закрыты вопросы Q5/Q6 (ADR-0001, ADR-0002) |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Ревью плана: версии сверены с Maven Central/документацией Kotlin; 3 minor (modules.xml, eol=lf в .gitattributes, порядок генерации wrapper) |
| 2026-10-23 | product-manager | READY → DRAFT | Дополнение пользователя «подключить Kotest для тестов» (разработка не начиналась): уточнены описание, AC-2, AC-3 (тесты на Kotest); добавлены AC-8 (только Kotest-спеки и assertions, версия зафиксирована) и AC-9 (граничный: Kotest-спеки обнаруживаются, выполнено ≥ 1 теста). Технический план требует пересмотра architect (JUnit → Kotest) |
| 2026-10-23 | architect | DRAFT → PLAN_REVIEW | План пересмотрен под Kotest (ADR-0005): Kotest 6.2.6 через `kotest-bom`, `kotest-runner-junit6` + `kotest-assertions-core`, `useJUnitPlatform { includeEngines("kotest") }`, `junit-jupiter` убран; smoke — `FunSpec` `BuildSmokeTest` ("tests run on the JDK 17 toolchain"), остаётся после T02; AC-9 — Gradle 9 `failOnNoDiscoveredTests` (не отключать). Учтены minor прошлого ревью (modules.xml, `eol=lf`, wrapper вне репозитория). Q7 закрыт ответом пользователя. Совместимость Kotest с Kotlin 2.3.21 / JDK 17 / JUnit 6.0.3 сверена по Maven Central |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Ревью плана под Kotest: kotest-bom/runner-junit6/assertions-core 6.2.6, id движка `kotest`, транзитивные версии JUnit 6.0.3 сверены по POM/jar; 2 minor (неточное обоснование junit5 vs junit6 в ADR-0005 → architect; доп. негатив AC-9 через неверный id движка). conventions.md «Тесты» обновлён под Kotest FunSpec |
| 2026-10-23 | architect | READY (без изменений) | Minor ревью №1 закрыт: обоснование `kotest-runner-junit6` vs `-junit5` переформулировано в ADR-0005 (таблица фактов, «Рассмотренные альтернативы») и architecture.md — движок общий (`kotest-runner-junit-platform`, исполняется на Platform 6.0.3 из BOM Boot), `-junit6` выбран, т.к. его POM объявляет JUnit Platform 6.0.3 = BOM Boot 4.1.2. План T01 не меняется |
| 2026-10-23 | kotlin-backend-developer | READY → IN_PROGRESS | Взята в работу |
| 2026-10-23 | kotlin-backend-developer | IN_PROGRESS → IN_REVIEW | Gradle 9.3.1 wrapper, Kotlin 2.3.21, toolchain 17, каталог версий, Kotest 6.2.6 (`BuildSmokeTest`, FunSpec); `./gradlew build` и `gradlew.bat build` зелёные (1 тест, 0 failures); негативы AC-3, AC-4, AC-9 (`kotest-typo`, `excludeEngines`) — `BUILD FAILED` как ожидается; отклонение: `exceptionFormat = FULL` ради сообщения о несовпадении (AC-3); открытый вопрос (не блокирует): пустая `FunSpec({})` даёт зелёную сборку; `gradlew` добавлен в индекс git с `+x` (без коммита) |
| 2026-10-23 | tech-lead | IN_REVIEW → IN_TESTING | Ревью кода: `./gradlew clean build` зелёный (1 тест), план п.1–9 соблюдён; 0 blocker / 0 major / 2 minor (→ architect: исправить негатив AC-9 в «Как проверить», отразить `exceptionFormat = FULL` в плане). Открытый вопрос AC-9 закрыт: пустая `FunSpec({})` — не сценарий AC-9, `failOnEmptyTestSuite` не включаем (ADR-0005); канонический негатив — `excludeEngines("kotest")` |
| 2026-10-23 | qa-engineer | IN_TESTING → DONE | 21 кейс (19 auto/скрипт, 2 manual): чистый клон с пустым `GRADLE_USER_HOME` — `./gradlew build` и `gradlew.bat build` зелёные; негативы AC-3, AC-4 (тест и main), AC-9 (`excludeEngines`, `kotest-typo`) роняют сборку; wrapper-jar sha256 = официальному; AC-7 без GUI подтверждён (`gradlew projects`, нет iml/modules.xml), импорт в IntelliJ — ручная проверка пользователя. Дефектов нет; info → T03: JUnit `@Test` компилируется и молча не исполняется до `ForbiddenImport` |
| 2026-10-23 | architect | DONE (без изменений) | Закрыты 2 minor «Ревью кода» (только документация): (1) «Как проверить», AC-9 — негатив «пустая `FunSpec({})` → падает» заменён на `includeEngines("kotest"); excludeEngines("kotest")` → `:test FAILED` «did not discover any tests» (доп. `includeEngines("kotest-typo")`), отмечено, что пустая спека Kotest считается одним тестом (ловится ревью); то же в таблице проверок architecture.md (строка «0 тестов»); (2) `testLogging { exceptionFormat = TestExceptionFormat.FULL }` + import внесены в эталонный `tasks.test` п.3 плана и в architecture.md (раздел «Задачи Gradle») |
