# F-001/T03: Автоматические проверки качества кода в сборке

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | DONE                             |
| Фича        | [F-001](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | F-001/T01, F-001/T02             |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Подключить к Gradle-сборке автоматические проверки стиля и качества Kotlin-кода (в `overview.md`
указаны ktlint / detekt; конкретный выбор и настройки — за architect). Проверки должны выполняться
в составе стандартной сборки и ронять её при нарушениях, чтобы код-ревью и QA опирались на единый
автоматический стандарт, а не на ручные замечания.

Допущение: проверки нужны уже сейчас и ронят `build` (Q3 в [feature.md](./feature.md)).

## Критерии приёмки
- [x] AC-1: Given текущий код проекта, When выполнить `./gradlew build`, Then проверки стиля/качества
  выполняются (видны в выводе задач Gradle) и проходят.
- [x] AC-2 (негативный): Given в Kotlin-файл внесено нарушение стиля (например, неверные отступы или
  неиспользуемый импорт), When выполнить `./gradlew build`, Then сборка падает, а сообщение указывает
  файл, строку и правило.
- [x] AC-3 (негативный): Given в коде есть нарушение статического анализа (например, слишком сложная функция
  или «магическое число» — по настроенным правилам), When выполнить `./gradlew build`, Then сборка падает
  с указанием места нарушения.
- [x] AC-4: Given разработчик хочет исправить стилевые нарушения, When он выполняет задокументированную
  команду автоформатирования (если инструмент её поддерживает), Then стилевые нарушения из AC-2 исправляются
  автоматически.
- [x] AC-5: Given проверки работают и на тестовом коде, When в тестах есть нарушение стиля, Then сборка
  также падает.
- [x] AC-6: Given новый участник команды, When он читает документацию проекта (`conventions.md` или README),
  Then там указаны команды запуска проверок и автоформатирования.

## Открытые вопросы
- ~~Q3~~ — закрыт: пользователь подтвердил, проверки нужны сразу и должны ронять сборку. Выбор инструментов — ADR-0004.

<!-- ============ architect ============ -->
## Технический план
Решение — [ADR-0004](../../architecture/adr/ADR-0004-code-quality-ktlint-detekt.md); граф задач `check` — [architecture.md](./architecture.md).
Зависимость от T02 добавлена architect'ом: T02 и T03 правят `build.gradle.kts`/`libs.versions.toml`, а проверки должны
проходить на реальном коде каркаса (AC-1).

- **Модули / пакеты:**
  1. `gradle/libs.versions.toml`:
     - `[versions]`: `ktlint = "1.8.0"`, `ktlint-gradle = "14.2.0"`, `detekt = "2.0.0-alpha.3"`;
     - `[plugins]`: `ktlint` (`org.jlleitschuh.gradle.ktlint`, version.ref ktlint-gradle), `detekt` (`dev.detekt`, version.ref detekt).
  2. `build.gradle.kts`:
     - `plugins { … alias(libs.plugins.ktlint); alias(libs.plugins.detekt) }`;
     - `ktlint { version = libs.versions.ktlint.get(); ignoreFailures = false }` — остальное по умолчанию (проверяются
       main, test и `*.gradle.kts`; `ktlintCheck` автоматически входит в `check`);
     - `detekt { toolVersion = libs.versions.detekt.get(); buildUponDefaultConfig = true; allRules = false;
       config.setFrom(file("config/detekt/detekt.yml")); source.setFrom("src/main/kotlin", "src/test/kotlin"); ignoreFailures = false;
       failOnSeverity = FailOnSeverity.Error }` — с `import dev.detekt.gradle.extensions.FailOnSeverity` в начале `build.gradle.kts`.
       `failOnSeverity` задаётся **явно**, чтобы порог падения не зависел от дефолтов alpha (согласовано с ADR-0004);
       задача `detekt` входит в `check` по умолчанию — **не** отключать; `detektMain`/`detektTest` в `check` не добавлять;
       отчёты по умолчанию (html + checkstyle) в `build/reports/detekt/`;
     - плагин `detekt-rules-ktlint-wrapper` (`detektPlugins(...)`) **не** подключать.
  3. `.editorconfig` (корень):
     ```
     root = true

     [*]
     charset = utf-8
     end_of_line = lf
     insert_final_newline = true
     trim_trailing_whitespace = true
     indent_style = space
     indent_size = 4

     [*.{kt,kts}]
     ktlint_code_style = ktlint_official
     max_line_length = 120
     # Deprecated in ktlint 1.x and disabled by default; enabled explicitly (ADR-0004). Revisit on ktlint 2.0.
     ktlint_standard_no-unused-imports = enabled

     [*.{yml,yaml,toml}]
     indent_size = 2

     [*.bat]
     end_of_line = crlf
     ```
     *Дополнено 2026-10-24 (решение по ревью кода T03, ADR-0004):* строка `ktlint_standard_no-unused-imports = enabled`
     и комментарий к ней добавлены в секцию `[*.{kt,kts}]`. В ktlint 1.8.0 правило `no-unused-imports` deprecated и
     по умолчанию выключено. Без этой строки неиспользуемые импорты не ловит ни ktlint, ни detekt: `style>UnusedImport`
     в alpha.3 требует type resolution и в задаче `detekt` молча пропускается. Поэтому `UnusedImport` в `detekt.yml`
     **не** включать, `detektMain`/`detektTest` в `check` **не** добавлять. Правило действует на main, test и `*.gradle.kts`.
  4. `config/detekt/detekt.yml` — **только дельта** к дефолтам:
     ```yaml
     style:
       MaxLineLength:
         active: false   # длиной строки владеет ktlint (.editorconfig max_line_length)
       ForbiddenImport:
         active: true    # тесты только на Kotest (ADR-0005)
         forbiddenImports:
           - value: 'org.junit.*'
             reason: 'Tests are Kotest specs (ADR-0005); JUnit API must not be used directly.'
           - value: 'org.assertj.*'
             reason: 'Use Kotest assertions (ADR-0005).'
           - value: 'org.hamcrest.*'
             reason: 'Use Kotest assertions (ADR-0005).'
           - value: 'kotlin.test.*'
             reason: 'Use Kotest assertions (ADR-0005).'
     ```
     `ForbiddenImport` (detekt 2.0.0-alpha.3, `dev.detekt.rules.style.ForbiddenImport`): ключ `forbiddenImports` — список glob-шаблонов
     с `value`/`reason` (`*` матчит и точки), есть в дефолтном конфиге alpha.3 (там `active: false`) — валидация конфига пройдёт; правило
     не требует type resolution, работает в задаче `detekt`; действует на main и test (в main эти импорты не нужны).
     Тесты — Kotest `FunSpec` со строковыми именами (`test("…") { }`), backtick-функций нет, поэтому правила именования функций
     (`FunctionNaming` detekt — к тому же исключает `**/test/**`; `function-naming` ktlint) на тестовый код не влияют и не переопределяются.
     Другие переопределения — только если правило конфликтует с ktlint или со стилем Kotest-спецификаций, с комментарием-причиной.
     Если в detekt 2.0 правило переименовано/перенесено — сверить по `./gradlew detektGenerateConfig` (сгенерированный полный
     файл не коммитить).
  5. Привести существующий код (включая `build.gradle.kts`, `settings.gradle.kts`) к правилам: `./gradlew ktlintFormat`, затем
     ручные правки. На `fun main` в `AutoStatisticApplication.kt` разрешён **единственный** `@Suppress("SpreadOperator")`
     с комментарием (если правило активно и срабатывает на `*args`). Иные подавления/baseline — запрещены.
  6. Документация (AC-6): в `docs/architecture/conventions.md` добавить раздел «Сборка и проверки» с командами:
     `./gradlew build` (всё), `./gradlew ktlintCheck detekt` (только проверки), `./gradlew ktlintFormat` (автоформат),
     где лежат отчёты, где конфигурация (`.editorconfig`, `config/detekt/detekt.yml`), и правило: подавления — только
     точечные с комментарием-причиной. (conventions.md ведёт tech-lead — правка проверяется на ревью кода.)
- **API-контракт:** нет.
- **Модель данных / миграции:** нет.
- **Доменные типы и ошибки:** нет.
- **Ключевые решения:** ADR-0004 (ktlint 1.8.0 через ktlint-gradle 14.2.0 + detekt 2.0.0-alpha.3, без baseline, без ktlint-wrapper;
  неиспользуемые импорты контролирует ktlint `no-unused-imports`, включённый явно в `.editorconfig`, — дополнение 2026-10-24);
  ADR-0005 (тесты — Kotest `FunSpec`; detekt `ForbiddenImport` для JUnit/AssertJ/Hamcrest/`kotlin.test`).
- **Как проверить (QA, временные правки — не коммитить):**
  - AC-1: `./gradlew build` → в выводе выполнены `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`,
    `:ktlintKotlinScriptCheck`, `:detekt`; `BUILD SUCCESSFUL`.
  - AC-2: лишний отступ / неиспользуемый импорт в main-файле → `BUILD FAILED`, сообщение `файл:строка:колонка … (standard:<rule>)`.
    Оба кейса проверяются отдельно:
    - отступ: например, 6 пробелов вместо 4 в теле `main` → `:ktlintMainSourceSetCheck FAILED`, `… (standard:indent)`;
    - неиспользуемый импорт: временно добавить `import java.util.UUID` в `AutoStatisticApplication.kt` →
      `:ktlintMainSourceSetCheck FAILED`, `…AutoStatisticApplication.kt:<строка>:1 Unused import (standard:no-unused-imports)`.
      Кейс работает только при строке `ktlint_standard_no-unused-imports = enabled` в `.editorconfig` (п.3). Если её нет, это
      дефект реализации T03, а не AC. Для AC-4 после этого кейса `./gradlew ktlintFormat` удаляет импорт, и `build` снова зелёный.
      Срабатывание `UnusedImport` в `:detekt` **не** ожидается: правило требует type resolution (ADR-0004).
  - AC-3: временно добавить в любой файл `src/main/kotlin` публичную top-level функцию, где число стоит **в выражении**,
    а не в инициализаторе свойства/локальной переменной:
    `fun timeoutMillis(): Long = 37L * 1000`
    → `./gradlew build` падает на `:detekt` с `файл:строка:колонка` и правилом `MagicNumber` (для `37L` и `1000`).
    Не использовать примеры вида `val x = 37 * 1000` / `const val`: в дефолтах detekt 2.0.0-alpha.3 у `MagicNumber`
    `ignorePropertyDeclaration: true`, `ignoreLocalVariableDeclaration: true`, `ignoreConstantDeclaration: true` — такие числа
    не ловятся (в 1.23 было иначе). Также не годятся именованные аргументы (`ignoreNamedArgument`) и тестовый код
    (`MagicNumber` по умолчанию исключает тесты). Альтернативный кейс: функция в main с цикломатической сложностью > 14
    (`CyclomaticComplexMethod`, `allowedComplexity: 14`), например `when` на 15+ ветвей → `:detekt FAILED` с правилом
    `CyclomaticComplexMethod`.
  - AC-4: после AC-2 `./gradlew ktlintFormat` → нарушение исправлено, `./gradlew build` зелёный.
  - AC-5: нарушение стиля в Kotest-спецификации из `src/test/kotlin` (например, лишний отступ внутри `test("…") { }`) →
    `:ktlintTestSourceSetCheck` падает.
  - Контроль «только Kotest» (ADR-0005, AC-6 фичи): временно добавить в любую спецификацию
    `import org.junit.jupiter.api.Test` и использовать его (например, `@Test fun x() {}` в отдельном классе того же файла), → `./gradlew build`
    падает на `:detekt` с `файл:строка:колонка` и правилом `ForbiddenImport` (с причиной из конфига).
  - AC-6: раздел в conventions.md присутствует.
- **Риски:** detekt alpha (ложные срабатывания, баги плагина). Версия 2.0.0-alpha.3 подтверждена пользователем и — единственная
  в таблице совместимости detekt под Gradle 9.3.1 + Kotlin 2.3.21. Если alpha.3 не работает с нашей сборкой:
  1) зафиксировать в «Реализации» точную ошибку (задача, стек, команда воспроизведения);
  2) переход на более новую 2.0.0-alpha.N допустим **только** если на нашем стеке (Gradle 9.3.1, Kotlin 2.3.21, JDK 17,
     Boot 4.1.2) проверенно зелёный полный `./gradlew build` (включая `:detekt` и негативную проверку AC-3); версия меняется
     в `libs.versions.toml` (и плагин, и `toolVersion`), результат проверки и причина — в «Реализации»;
  3) иначе — **эскалация к architect** (через оркестратора), без самостоятельных обходов: отключение detekt, `ignoreFailures`,
     baseline, понижение Kotlin/Gradle — запрещены.
  Примечание: по таблице совместимости более новые alpha (напр. alpha.6) собраны под Gradle 9.6.1 / Kotlin 2.4.10, поэтому
  их работа на нашем стеке не гарантирована — отсюда требование проверенно зелёной сборки.
  Синтаксис `ktlint { version = … }` / `detekt { … }` с `=` для `Property` поддерживается Gradle 9 Kotlin DSL; при ошибке — `.set(...)`.

  **Если в T02 применён запасной вариант ADR-0005** (Spring-тесты на JUnit Jupiter — см. «Реализацию» T02 и отметку в шапке ADR-0005;
  решение пользователя 2026-10-23, без повторного согласования), п.4 и проверка «только Kotest» выполняются так:
  - в `config/detekt/detekt.yml` — **белый список**: `forbiddenImports` остаётся **как в п.4** (`org.junit.*`, `org.assertj.*`,
    `org.hamcrest.*`, `kotlin.test.*` — без изменений), в правило добавляется ключ `allowedImports` (на одном уровне с `forbiddenImports`):
    ```yaml
       ForbiddenImport:
         active: true
         forbiddenImports:
           # … как в п.4, без изменений
         allowedImports:
           - 'org.junit.jupiter.api.Test'   # только для @SpringBootTest-классов, запасной вариант ADR-0005
    ```
    Семантика (сверено tech-lead по байткоду detekt 2.0.0-alpha.3): находка выдаётся, если FqName импорта **полностью** совпал с шаблоном
    из `forbiddenImports` **и** не совпал ни с одним шаблоном `allowedImports` (`allowedImports` — список строк-шаблонов, ключ есть в дефолтном
    конфиге alpha.3 → `config.validation` пройдёт). Поэтому разрешён ровно `org.junit.jupiter.api.Test`; всё прочее из `org.junit.*` запрещено —
    `Assertions`/`Assumptions`, Kotlin-API `org.junit.jupiter.api.assertThrows`/`assertDoesNotThrow`/`assertAll`/`fail` и т.п., `@Disabled`,
    `@Nested`, `@BeforeEach`, `import org.junit.jupiter.api.*` (FqName `org.junit.jupiter.api` с шаблоном `org.junit.jupiter.api.Test` не совпадает),
    JUnit 4 (`org.junit.Test`, `org.junit.Assert`). Точечный чёрный список (запрет только `Assertions*`/JUnit 4) **не используется**: он пропускает
    Kotlin-проверки JUnit и будущие API Jupiter. Расширение белого списка (например, `@BeforeEach`) — только правкой ADR-0005 и conventions.md через ревью.
    Ограничение «JUnit `@Test` — только в классах с `@SpringBootTest`» detekt не выражает (у `ForbiddenImport` нет исключений по файлам) —
    контролируется ревью кода (чек-лист conventions.md).
  - негативная проверка «только Kotest» (не коммитить; запускать `./gradlew detekt` или `build --continue`, см. круг 3): вместо
    `import org.junit.jupiter.api.Test` временно добавить в любой тест каждый из импортов (с использованием) →
    `:detekt` падает с `файл:строка:колонка`, правилом `ForbiddenImport` и причиной из конфига для **каждого**:
    - `import org.junit.jupiter.api.Assertions.assertEquals`;
    - `import org.junit.jupiter.api.assertThrows` (Kotlin-API Jupiter, напр. `assertThrows<IllegalStateException> { error("x") }`);
    - `import org.assertj.core.api.Assertions.assertThat`.
  - позитивная проверка: Spring-тесты запасного варианта (`AutoStatisticApplicationTest`, `HealthEndpointTest`) с `import org.junit.jupiter.api.Test`
    проходят `./gradlew detekt` **без находок** `ForbiddenImport` (и весь `./gradlew build` зелёный — AC-1).
  - ktlint `function-naming` допускает backtick-имена в файлах с импортом JUnit, detekt `FunctionNaming` исключает `**/test/**` —
    JUnit-методы Spring-тестов (``fun `health endpoint returns 200 with status UP`()``) правил именования не нарушают; переопределения не нужны.
  Остальной план T03 и AC — без изменений.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **NEEDS_REWORK** → architect (точечные правки «Технического плана» и ADR-0004; декомпозиция и AC — без замечаний, к PM вопросов нет)
- Проверено (2026-10-23, по артефактам в Maven Central / Gradle Plugin Portal и detekt.dev):
  - Таблица совместимости detekt: `2.0.0-alpha.3 — Gradle 9.3.1, Kotlin 2.3.21, JDK до 25`, байткод плагина и `detekt-core` — JVM 8 →
    работает на JDK 17. Выбор alpha.3 обоснован: это единственная версия detekt, протестированная ровно на нашем стеке.
  - API расширения `dev.detekt` 2.0.0-alpha.3 (`DetektExtension`): `toolVersion`, `config`, `source`, `buildUponDefaultConfig`, `allRules`,
    `ignoreFailures`, `failOnSeverity` — всё, что использует план, существует. Задача `detekt` по умолчанию входит в `check` (документация 2.0.0-alpha.3).
  - Дефолтный конфиг alpha.3: `config.validation: true` (опечатка в `detekt.yml` уронит сборку — хорошо); `style>MaxLineLength` существует
    (active, 120) — ключ из п.4 корректен; `performance>SpreadOperator` активен для main → `@Suppress("SpreadOperator")` на `main` понадобится;
    `naming>FunctionNaming` исключает `**/test/**` — backtick-имена тестов не сломают detekt.
  - ktlint-gradle 14.2.0 (Gradle 9 поддерживается с 13.1.0) и ktlint 1.8.0 существуют; правило ktlint `function-naming` допускает backtick-имена
    при импорте JUnit.
- Замечания:
  - [major] «Как проверить», AC-3: пример `val timeoutMs = 37 * 1000` **не сработает** — в дефолтах detekt 2.0.0-alpha.3 у `MagicNumber`
    `ignorePropertyDeclaration: true` и `ignoreLocalVariableDeclaration: true` (в 1.23 было иначе). QA получит «зелёную» сборку и ложный дефект.
    → Заменить пример на гарантированно срабатывающий в main-коде: число в выражении/аргументе вызова, например
    `fun timeoutMillis(): Long = 37L * 1000` (не объявление свойства/переменной) или функция с `CyclomaticComplexMethod` > 14 (`allowedComplexity: 14`).
  - [minor] План расходится с ADR-0004: в ADR зафиксировано `failOnSeverity = Error`, в п.2 плана параметра нет. → Указать явно
    `failOnSeverity = FailOnSeverity.Error` (`dev.detekt.gradle.extensions.FailOnSeverity`), чтобы порог падения не зависел от дефолтов alpha.
  - [minor] Фоллбек «перейти на 2.0.0-alpha.6» (план, «Риски»; ADR-0004, «Последствия») не обоснован совместимостью: по таблице detekt alpha.6
    собран под Gradle 9.6.1 / Kotlin 2.4.10 (alpha.4 в таблице вообще нет), т.е. на Gradle 9.3.1 он не гарантирован так же, как alpha.3.
    → Переформулировать: при сбое alpha.3 сначала зафиксировать ошибку; переход на более новую alpha допустим, только если она проверенно работает
    на Gradle 9.3.1 + Kotlin 2.3.21 (зелёный `./gradlew build`), иначе — эскалация к architect. Обновить ADR-0004 тем же текстом.
- Оценка риска detekt alpha (для оркестратора): **приемлемый**. Риски — ложные срабатывания и смена схемы конфига между alpha; смягчены
  фиксированной версией, дельта-конфигом и валидацией конфига. Блокирующей несовместимости в связке Gradle 9.3.1 / Kotlin 2.3.21 / Boot 4.1.2 /
  JDK 17 не найдено. Ограничение на будущее: обновление Kotlin/Gradle «заперто» на detekt — закреплено в conventions.md.

### Круг 2 (2026-10-23)
- Вердикт: **READY**
- Проверка исправлений:
  - [major] AC-3 — исправлено: в `fun timeoutMillis(): Long = 37L * 1000` числа стоят в выражении тела функции в `src/main/kotlin`,
    поэтому на них не действуют `ignorePropertyDeclaration` / `ignoreLocalVariableDeclaration` / `ignoreConstantDeclaration` / `ignoreNamedArgument`,
    и их нет в `ignoreNumbers` (`-1, 0, 1, 2`) → `MagicNumber` сработает на `37L` и `1000`. Неработающие варианты перечислены явно;
    альтернативный кейс `CyclomaticComplexMethod` (> 14) корректен.
  - [minor] `failOnSeverity = FailOnSeverity.Error` с импортом `dev.detekt.gradle.extensions.FailOnSeverity` задан явно в п.2 и совпадает с ADR-0004.
  - [minor] Запасной вариант с alpha одинаково переформулирован в плане, ADR-0004 («Последствия») и architecture.md («Риски»):
    фиксация ошибки → новая alpha только при проверенно зелёном полном `./gradlew build` (включая `:detekt` и негативную проверку AC-3) на нашем стеке →
    иначе эскалация к architect; обходы (отключение detekt, `ignoreFailures`, baseline, понижение Kotlin/Gradle) запрещены.
- Согласованность: версии (ktlint 1.8.0 / ktlint-gradle 14.2.0 / detekt 2.0.0-alpha.3), состав `check`, отказ от ktlint-wrapper и baseline,
  единственный `@Suppress("SpreadOperator")` на `main` — одинаковы в плане, ADR-0004, architecture.md и conventions.md («Сборка и зависимости»).
- Новых замечаний нет.

### Круг 3 — сверка с Kotest (2026-10-23)
- Вердикт: **READY**
- Проверено по jar `detekt-rules-style` / `detekt-api` / `detekt-core` 2.0.0-alpha.3:
  - `dev.detekt.rules.style.ForbiddenImport` читает ключи `forbiddenImports` (список `value`/`reason`) и `allowedImports`; в дефолтном конфиге
    `active: false`, `forbiddenImports: []`, `allowedImports: []` — дельта из п.4 пройдёт `config.validation`.
  - Шаблон преобразуется `simplePatternToRegex`: `.` → `\.`, `*` → `.*` — `org.junit.*` матчит `org.junit.jupiter.api.Test`, `org.junit.platform.*` и т.д.;
    `io.kotest.*`, `org.springframework.*` (в т.ч. `org.springframework.boot.test.context.SpringBootTest`) не задеваются.
  - У `ForbiddenImport` нет `excludes`, у набора `style` нет excludes на уровне rule set — правило действует и на `src/test/kotlin` (что и нужно).
  - Правило анализирует только директивы `import` исходников проекта — транзитивные зависимости (`junit-jupiter`, `assertj`, `hamcrest` из
    `spring-boot-starter-test`) на classpath не влияют и не исключаются; ни один нужный импорт плана T01/T02 под запрет не попадает
    (Kotest-матчеры, `SpringExtension`, `@ApplyExtension`, `RestTestClient`, `ParameterizedTypeReference`, `PortInUseException`).
  - `MagicNumber`/`SpreadOperator` исключают `**/test/**` — `shouldBe 200/404` в спеках не сработают; строковые имена `FunSpec` не затрагивают правила именования.
- Замечания (не блокируют):
  - [minor] «Как проверить», контроль «только Kotest»: `./gradlew build` без `--continue` останавливается на первой упавшей задаче — при нарушении стиля
    в той же временной правке может упасть `ktlint…Check` раньше `:detekt`, и `ForbiddenImport` не будет виден. → Для этого кейса запускать
    `./gradlew detekt` (или `build --continue`) и проверять в выводе именно `ForbiddenImport` с причиной из конфига.

### Круг 4: запасной вариант ADR-0005, `ForbiddenImport` (2026-10-23)
- Вердикт: **NEEDS_REWORK** → architect (только блок «Если в T02 применён запасной вариант ADR-0005» и та же строка в ADR-0005 / architecture.md;
  основной план, AC и декомпозиция замечаний не имеют)
- Проверено по байткоду `dev.detekt.rules.style.ForbiddenImport` 2.0.0-alpha.3: правило берёт `KtImportDirective.importedFqName`
  (для `import a.b.*` это `a.b`) и сравнивает **полным** `Regex.matches` с шаблонами `simplePatternToRegex` (`.`→`\.`, `*`→`.*`, `?`→`.`;
  регистр учитывается). Отчёт выдаётся, только если импорт совпал с `forbiddenImports` **и** не совпал ни с одним `allowedImports`
  (`allowedImports` — список строк-шаблонов, ключ есть в дефолтном конфиге alpha.3).
  - Точечные шаблоны не блокируют `org.junit.jupiter.api.Test`: полное совпадение с `org\.junit\.Test`, `org\.junit\.Assert.*` и прочими невозможно. Это верно.
  - `org.junit.jupiter.api.Assertions.assertEquals` и `Assertions.*` (FqName `…Assertions`) блокируются, поэтому негативная проверка из плана сработает.
- Замечания:
  - [major] Точечный blacklist пропускает запрещённые JUnit-проверки. В `junit-jupiter-api` есть Kotlin-API верхнего уровня в пакете
    `org.junit.jupiter.api` (`AssertionsKt`): `assertThrows`, `assertDoesNotThrow`, `assertAll`, `assertTimeout`, `assertTimeoutPreemptively`,
    `assertInstanceOf`, `assertNull`, `assertNotNull`, `fail`. Их импортируют как `import org.junit.jupiter.api.assertThrows`, и шаблон
    `org.junit.jupiter.api.Assertions*` их не ловит (другое имя, регистр учитывается). Для Kotlin-теста на JUnit это самый естественный
    способ проверки, а ADR-0005 обещает его автоматический запрет. Также проходят `import org.junit.jupiter.api.*` (FqName `org.junit.jupiter.api`),
    `@Disabled` (отключение тестов запрещено планом T02) и `@Nested` (не используется по ADR). → Заменить blacklist на whitelist:
    `forbiddenImports` оставить как в основном плане (`org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*`, без изменений) и добавить
    ```yaml
         allowedImports:
           - 'org.junit.jupiter.api.Test'
    ```
    Это совпадает с п.5 «Рисков» T02 («Импорты JUnit — только `org.junit.jupiter.api.Test`»), закрывает и JUnit 4, и будущие API Jupiter.
    Если понадобится расширить список (например, `@BeforeEach`), это делается правкой ADR-0005/conventions.md через ревью.
    Обновить так же таблицу ADR-0005 (строка «detekt `ForbiddenImport`») и `architecture.md` F-001 («Риски», запасной вариант).
  - [minor] Негативная проверка запасного варианта: кроме `Assertions.assertEquals` и AssertJ временно добавить `import org.junit.jupiter.api.assertThrows`
    (с использованием) → `ForbiddenImport`. Позитивная проверка: Spring-тесты с `import org.junit.jupiter.api.Test` проходят `./gradlew detekt` без находок.

### Круг 5: повторное ревью запасного варианта ADR-0005 (2026-10-23)
- Вердикт: **READY**
- Проверка исправлений круга 4:
  - [major] закрыт. В блоке «Если в T02 применён запасной вариант ADR-0005» `forbiddenImports` оставлен как в п.4 (`org.junit.*`, `org.assertj.*`,
    `org.hamcrest.*`, `kotlin.test.*`), добавлен `allowedImports: ['org.junit.jupiter.api.Test']` на одном уровне с `forbiddenImports`. Семантика
    (полное совпадение FqName с запрещённым шаблоном и несовпадение ни с одним разрешённым) описана верно и совпадает с байткодом alpha.3:
    разрешён ровно `org.junit.jupiter.api.Test`. `org.junit.jupiter.api.assertThrows`/`assertAll`/`fail`, `Assertions`/`Assumptions`, `@Disabled`, `@Nested`,
    `@BeforeEach`, `import org.junit.jupiter.api.*` (FqName `org.junit.jupiter.api`) и JUnit 4 по-прежнему ловятся. Отказ от точечного чёрного списка
    обоснован, порядок расширения белого списка (через ADR-0005 и conventions.md) зафиксирован. Ограничение «`@Test` только в `@SpringBootTest`-классах»
    честно отнесено к ревью кода.
  - [minor] закрыт (проверки в T03). Негативная проверка: `Assertions.assertEquals`, `org.junit.jupiter.api.assertThrows` (с использованием),
    AssertJ `assertThat`. Для каждого ожидается `ForbiddenImport` с причиной. Запуск через `./gradlew detekt` / `build --continue` (учтён minor круга 3).
    Позитивная проверка: `AutoStatisticApplicationTest` и `HealthEndpointTest` с `import org.junit.jupiter.api.Test` проходят без находок, `build` зелёный.
  - [minor] закрыт (перечень правок документов). Пункт «T03» в «Обязательных правках» ADR-0005 совпадает с планом T03 (конфиг, негативные и позитивная
    проверки). Пункт «Фреймворк» в conventions.md упоминает `allowedImports`. Строка «Нет JUnit/AssertJ-тестов» в architecture.md описана как
    «белый список + ревью». В п.6 «Рисков» T02 сделана ссылка на белый список в T03.
- Согласованность: T03 (блок запасного варианта), ADR-0005 (п.6 «Решения», таблица «Что меняется», «Обязательные правки»), architecture.md
  («Риски», запасной вариант), T02 (п.5 и п.6 «Рисков»: «Импорты JUnit — только `org.junit.jupiter.api.Test`»), feature.md (Q8: «белый список, из JUnit разрешён
  только `org.junit.jupiter.api.Test`») описывают одно и то же решение. Упоминания «точечных шаблонов» / «сужения» остались только в исторических записях журналов.
  Основной план (п.4) и ADR-0004 не затронуты и с запасным вариантом не конфликтуют.
- Новых замечаний нет.

<!-- ============ kotlin-backend-developer ============ -->
## Реализация
- **Изменённые файлы:**
  - `gradle/libs.versions.toml`: версии `ktlint = "1.8.0"`, `ktlint-gradle = "14.2.0"`, `detekt = "2.0.0-alpha.3"`; плагины `ktlint`, `detekt`.
  - `build.gradle.kts`: плагины `ktlint`/`detekt`, блоки `ktlint { }` и `detekt { }` по п.2 плана, `import dev.detekt.gradle.extensions.FailOnSeverity`.
    `exceptionFormat = TestExceptionFormat.FULL` и `includeEngines("kotest")` сохранены.
  - `.editorconfig` (новый): дословно по п.3.
  - `config/detekt/detekt.yml` (новый): дельта по п.4 (основной вариант ADR-0005). `MaxLineLength` выключен, `ForbiddenImport` запрещает
    `org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*`, `allowedImports` нет.
  - `src/test/kotlin/com/autostatistic/{AutoStatisticApplicationTest,BuildSmokeTest,HealthEndpointTest,PortInUseTest,ServerPortOverrideTest}.kt`:
    только автоформат `./gradlew ktlintFormat` под `ktlint_official`, логика тестов не менялась. Изменения: перенос супертипа `FunSpec({…})` на новую строку
    и параметра конструктора на отдельную строку (`class-signature`), отступ тела лямбды, цепочки `client.get().uri(…)…` и
    `contentType.shouldNotBeNull().isCompatibleWith(…)` разбиты по строкам (`chain-method-continuation`), `runCatching { }` и
    `HttpClient…send(…)` перенесены после `=` (`multiline-expression-wrapping`).
  - `src/main/kotlin/.../AutoStatisticApplication.kt`, `settings.gradle.kts`: без изменений, ktlint/detekt проходят как есть.
  - `docs/architecture/conventions.md`: новый раздел «Сборка и проверки» (AC-6). В нём команды `build`, `ktlintCheck detekt`, `ktlintFormat`, `detekt`
    (и `--continue`), где лежат отчёты и конфигурация, правило подавлений, оговорка про неиспользуемые импорты.
- **Что сделано:** ktlint и detekt подключены к `check`, поэтому `build` падает при нарушении в main, test и `*.gradle.kts`. Порог detekt задан явно
  (`failOnSeverity = Error`), baseline и ktlint-wrapper не подключены. Подавлений в коде нет: `SpreadOperator` в detekt 2.0.0-alpha.3 без type resolution
  на `runApplication(*args)` не срабатывает, поэтому разрешённый планом `@Suppress("SpreadOperator")` не понадобился. detekt alpha.3 на нашем стеке
  работает, менять версию не пришлось.
- **Тесты / проверки** (запуск: `./gradlew clean build`; временные правки откачены, рабочее дерево чистое):
  - AC-1: `clean build` → выполнены `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`, `:ktlintKotlinScriptCheck`, `:detekt`, `:test`
    (10 тестов, 10 PASSED, 0 failed/skipped) → `BUILD SUCCESSFUL`. Отчёты лежат в `build/reports/ktlint/*/` и `build/reports/detekt/detekt.{html,xml,sarif,md}`.
  - AC-2: отступ 6 вместо 4 в `main` (`AutoStatisticApplication.kt`) → `:ktlintMainSourceSetCheck FAILED`,
    `…AutoStatisticApplication.kt:10:1 Unexpected indentation (6) (should be 4) (standard:indent)`.
  - AC-3: `fun timeoutMillis(): Long = 37L * 1000` в `AutoStatisticApplication.kt` → `:detekt FAILED`,
    `…AutoStatisticApplication.kt:13:29 … [MagicNumber]` и `…:13:35 … [MagicNumber]`.
  - AC-4: после нарушения из AC-2 `./gradlew ktlintFormat` → `BUILD SUCCESSFUL`, файл побайтно совпал с исходным.
  - AC-5: лишний отступ внутри `test("…") { }` в `BuildSmokeTest.kt` → `:ktlintTestSourceSetCheck FAILED`,
    `…BuildSmokeTest.kt:9:1 Unexpected indentation (15) (should be 12) (standard:indent)`.
  - «Только Kotest» (ADR-0005, замечание QA по T01): в `BuildSmokeTest.kt` добавлен отдельный класс без Spring `class SilentJUnitTest { @Test fun x() { assertThat(1).isEqualTo(1) } }`
    с `import org.junit.jupiter.api.Test` и `import org.assertj.core.api.Assertions.assertThat`. И `./gradlew detekt`, и `build --continue` → `:detekt FAILED`:
    `BuildSmokeTest.kt:6:1 The import 'org.junit.jupiter.api.Test' has been forbidden: Tests are Kotest specs (ADR-0005); JUnit API must not be used directly. [ForbiddenImport]`
    и `:5:1 … 'org.assertj.core.api.Assertions.assertThat' … Use Kotest assertions (ADR-0005). [ForbiddenImport]`.
    Дополнительно проверены `kotlin.test.assertEquals`, `org.hamcrest.MatcherAssert.assertThat` и `import org.junit.jupiter.api.*`: все три дают `[ForbiddenImport]` с причиной.
  - AC-6: раздел «Сборка и проверки» в `conventions.md`.
- **Отклонения от плана:**
  1. `ktlint { version = libs.versions.ktlint.asProvider() }` вместо `libs.versions.ktlint.get()`. При ключах `ktlint` и `ktlint-gradle` в каталоге
     `libs.versions.ktlint` становится группой, и `.get()` не компилируется. `asProvider()` даёт `Provider<String>`, его присваивание `Property` поддерживается.
     Для единообразия `toolVersion = libs.versions.detekt` присваивается провайдером, без `.get()`. Поведение то же.
  2. Добавлен `verbose = true` в `ktlint { }`. Без него консольный вывод ktlint-gradle не показывает идентификатор правила (`(standard:indent)`).
     Правило было только в файле отчёта, а AC-2 требует видеть в сообщении файл, строку и правило.
  3. Замечание к AC-2 (для architect/tech-lead, не блокирует): «неиспользуемый импорт» по умолчанию не ловится. В ktlint 1.8.0 правило `no-unused-imports`
     помечено `@Deprecated` («Marked for removal in Ktlint 2.0.0») и без явного `ktlint_standard_no-unused-imports = enabled` не запускается
     (с этим ключом проверено: `Unused import (standard:no-unused-imports)`). `style>UnusedImport` в detekt по умолчанию тоже неактивно.
     По плану `.editorconfig` и `detekt.yml` строго фиксированы, поэтому я их не трогал. AC-2 подтверждён примером с отступом (AC даёт его как
     равноправный вариант), оговорка записана в conventions.md. Если нужен автоматический контроль неиспользуемых импортов, решение за architect: включить
     `UnusedImport` в detekt или устаревшее правило ktlint.

### Дополнение (2026-10-24): неиспользуемые импорты, решение architect (ADR-0004)
Это доработка по решению architect к отклонению 3, а не круг `CHANGES_REQUESTED`.
- **Изменённые файлы:** `.editorconfig`, секция `[*.{kt,kts}]`, сразу после `max_line_length = 120`:
  `# Deprecated in ktlint 1.x and disabled by default; enabled explicitly (ADR-0004). Revisit on ktlint 2.0.` и
  `ktlint_standard_no-unused-imports = enabled`. Другие файлы не менялись. conventions.md не трогал, его оговорку поправит tech-lead.
- **Проверки** (временная правка откачена):
  - `./gradlew clean build` с новым правилом → `BUILD SUCCESSFUL`, 10/10 PASSED. Существующий код (main, test, `*.gradle.kts`) неиспользуемых импортов не содержит.
  - AC-2 (неиспользуемый импорт): добавлен `import java.util.UUID` в `AutoStatisticApplication.kt` → `:ktlintMainSourceSetCheck FAILED`,
    `…AutoStatisticApplication.kt:5:1 Unused import (standard:no-unused-imports)`.
  - AC-4: `./gradlew ktlintFormat` → `BUILD SUCCESSFUL`, импорт удалён, файл побайтно совпал с исходным.
  - После отката `./gradlew clean build` → `BUILD SUCCESSFUL`: выполнены `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`,
    `:ktlintKotlinScriptCheck`, `:detekt`, 10/10 PASSED.

<!-- ============ tech-lead ============ -->
## Ревью кода
- Вердикт: **IN_TESTING** (0 blocker / 0 major / 3 minor)
- Проверено (2026-10-24, рабочее дерево, изменения не закоммичены):
  - `./gradlew clean build` → **BUILD SUCCESSFUL**. Выполнены `:detekt`, `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`,
    `:ktlintKotlinScriptCheck`, `:test`: 10/10 PASSED, все имена совпадают с T02 (AC-1).
  - Каталог, `build.gradle.kts`, `.editorconfig`, `detekt.yml` соответствуют п.1–4 плана. `failOnSeverity = FailOnSeverity.Error` задан явно,
    baseline и ktlint-wrapper не подключены, `detektMain`/`detektTest` в `check` не добавлены. `@Suppress` нет нигде (в detekt без type resolution
    `SpreadOperator` на `*args` не срабатывает, поэтому разрешённое подавление не понадобилось). `!!`, `org.junit`, `org.assertj` в `src/` нет.
  - Переформатирование тестов `ktlintFormat` логику не изменило. Сверил все 5 спецификаций: те же `test("…")` и те же матчеры/ожидания
    (200/404/405, `problem+json`, `PortInUseException.port`, `local.server.port`), given/when/then-комментарии на месте. Изменились только
    переносы (`class-signature`, `chain-method-continuation`, `multiline-expression-wrapping`) и отступ тела лямбды `FunSpec({ … })`.
  - AC-6: раздел «Сборка и проверки» в `conventions.md` содержит команды, отчёты, конфигурацию и правило подавлений. Принят (с minor ниже).
- Оценка отклонений из «Реализации»:
  1. `libs.versions.ktlint.asProvider()` — **принято**. Ключи `ktlint` и `ktlint-gradle` делают `libs.versions.ktlint` группой, и `.get()`
     не компилируется. `asProvider()` — штатный способ, ленивое присваивание `Property<String>` корректно. `toolVersion = libs.versions.detekt`
     без `.get()` эквивалентен плану.
  2. `verbose = true` — **принято**. Без этого флага ktlint-gradle не печатает id правила в консоль, а AC-2 требует файл, строку и правило
     в сообщении. Проверено: `…AutoStatisticApplication.kt:5:1 Unused import (standard:no-unused-imports)`.
  3. Неиспользуемые импорты не контролируются — **принято с оговоркой, вопрос к architect** (не блокирует, AC-2 выполнен на примере с отступом;
     AC называет примеры через «или»). Проверено временными правками (откачены, `cmp` с исходниками совпал, `build` зелёный):
     - по умолчанию `import java.util.UUID` в main проходит и `ktlint…Check`, и `detekt` — подтверждено;
     - **`style>UnusedImport: active: true` в `detekt.yml` проблему не решает.** В detekt 2.0.0-alpha.3 правило помечено `@RequiresAnalysisApi`
       и в задаче `detekt` (без type resolution) молча пропускается: `build` остаётся зелёным. Ловится только в `detektMain`
       (`AutoStatisticApplication.kt:5:1 The import 'java.util.UUID' is unused. [UnusedImport]`). Но `detektMain` план запрещает включать в `check`,
       и в нём сразу срабатывает `SpreadOperator` на `runApplication(*args)`. Поэтому требовать от разработчика «включить UnusedImport в detekt.yml»
       бессмысленно: так контроль не появится, а изменение графа `check` — решение architect (ADR-0004);
     - `ktlint_standard_no-unused-imports = enabled` в секции `[*.{kt,kts}]` `.editorconfig` работает: `ktlintMainSourceSetCheck FAILED …:5:1 Unused import
       (standard:no-unused-imports)`, а `ktlintFormat` удаляет импорт (подходит и под AC-4). Минус: правило deprecated, его удалят в ktlint 2.0.
- Замечания:
  - [minor] → architect. `T03-code-quality-checks.md` («Как проверить», AC-2) и ADR-0004: контроль неиспользуемых импортов не обеспечен
    выбранной конфигурацией, а в «Как проверить» AC-2 неиспользуемый импорт указан как рабочий пример. QA получит ложный дефект.
    → Принять решение и зафиксировать в ADR-0004. Рекомендация tech-lead: отдельной правкой (вне T03 или повторным кругом по решению architect) добавить в `.editorconfig`
    `ktlint_standard_no-unused-imports = enabled` с комментарием «deprecated, пересмотреть при переходе на ktlint 2.0». Это одна строка, вывод
    `файл:строка:правило`, автоисправление `ktlintFormat`. Вариант с `detektMain`/`detektTest` в `check` дороже: type resolution, `@Suppress("SpreadOperator")`
    и возможные новые находки alpha. До решения: в «Как проверить» AC-2 оставить только пример с отступом (QA — использовать его).
  - [minor] `docs/architecture/conventions.md:62-63` — формулировка «в detekt тоже (`UnusedImport` неактивно)» подталкивает к неработающему фиксу.
    → Уточнить: «`UnusedImport` detekt 2.0 требует type resolution (`detektMain`) и в задаче `detekt` не выполняется». Править по итогам решения
    architect по замечанию выше (conventions.md ведёт tech-lead, правка сделается вместе с ним).
  - [minor] `docs/architecture/conventions.md:58` — «формат `файл:строка:колонка сообщение (правило)`» верен только для ktlint. У detekt
    `e: файл:строка:колонка сообщение [Правило]`. → Указать оба формата (можно в той же правке).

### Повторное ревью: «Дополнение (2026-10-24)», неиспользуемые импорты
- Вердикт: **IN_TESTING** (0 blocker / 0 major / 0 minor, открытых замечаний нет)
- Проверено:
  - `.editorconfig`: в секции `[*.{kt,kts}]` есть `ktlint_standard_no-unused-imports = enabled` и комментарий-причина («deprecated…, ADR-0004,
    revisit on ktlint 2.0»). Это совпадает с п.3 плана, «Как проверить» AC-2 и ADR-0004. Других изменений нет.
  - `./gradlew clean build` (запустил сам) → **BUILD SUCCESSFUL**. Выполнены `:detekt`, `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`,
    `:ktlintKotlinScriptCheck`, `:test`. Существующий код неиспользуемых импортов не содержит.
  - Негативная проверка на тестовом коде (временная правка, откачена): `import java.util.UUID` в `BuildSmokeTest.kt` →
    `:ktlintTestSourceSetCheck FAILED`, `BuildSmokeTest.kt:5:1 Unused import (standard:no-unused-imports)`. После `./gradlew ktlintFormat`
    файл побайтно совпал с исходным (`cmp`). Кейс для main подтверждён разработчиком и в первом круге ревью.
- Замечания первого круга закрыты:
  - [minor] → architect (контроль неиспользуемых импортов) — закрыт решением architect (ADR-0004, план п.3, «Как проверить» AC-2) и правкой разработчика.
  - [minor] `conventions.md`, неиспользуемые импорты — закрыт tech-lead. Теперь там сказано, что их контролирует ktlint `no-unused-imports`, явно
    включённый в `.editorconfig`. Правило deprecated и требует пересмотра при переходе на ktlint 2.0. detekt `UnusedImport` требует type resolution
    и в задаче `detekt` не выполняется.
  - [minor] `conventions.md`, формат вывода — закрыт tech-lead. Указаны оба формата: ktlint `файл:строка:колонка сообщение (standard:<правило>)`,
    detekt `e: файл:строка:колонка сообщение [<Правило>]`.

<!-- ============ qa-engineer ============ -->
## Тестирование
Проверка 2026-10-24, рабочая копия (изменения не закоммичены). Временные нарушения вносились в копию файлов и откатывались
из резервной копии (`diff -r` с бэкапом пустой). Итоговый `./gradlew clean build` → **BUILD SUCCESSFUL**, 10/10 PASSED.
Автотесты не добавлялись: все AC T03 про поведение сборки (падение `build` на временных нарушениях). Код с нарушениями
нельзя держать в `src/test`, а Gradle TestKit для вложенных сборок план не предусматривает. Поэтому кейсы — ручные
прогоны команд Gradle, все воспроизводимы по таблице.

| # | Кейс | Проверяет AC | Тип (auto/manual) | Результат |
|---|------|--------------|-------------------|-----------|
| 1 | `./gradlew clean build` на текущем коде: выполнены `:ktlintMainSourceSetCheck`, `:ktlintTestSourceSetCheck`, `:ktlintKotlinScriptCheck`, `:detekt`, `:test` (10/10 PASSED) | AC-1 | manual | PASS |
| 2 | main: 6 пробелов вместо 4 в теле `main` → `:ktlintMainSourceSetCheck FAILED`, `AutoStatisticApplication.kt:10:1 Unexpected indentation (6) (should be 4) (standard:indent)` | AC-2 | manual | PASS |
| 3 | main: `import java.util.UUID` → `:ktlintMainSourceSetCheck FAILED`, `AutoStatisticApplication.kt:5:1 Unused import (standard:no-unused-imports)` | AC-2 | manual | PASS |
| 4 | test: лишний отступ внутри `test("…") { }` в `BuildSmokeTest.kt` → `:ktlintTestSourceSetCheck FAILED`, `BuildSmokeTest.kt:9:1 Unexpected indentation (15) (should be 12) (standard:indent)` | AC-2, AC-5 | manual | PASS |
| 5 | test: `import java.util.UUID` в `BuildSmokeTest.kt` → `:ktlintTestSourceSetCheck FAILED`, `BuildSmokeTest.kt:5:1 Unused import (standard:no-unused-imports)` | AC-2, AC-5 | manual | PASS |
| 6 | `fun timeoutMillis(): Long = 37L * 1000` в main → `:detekt FAILED`, `e: …AutoStatisticApplication.kt:13:29 … [MagicNumber]` и `:13:35 … [MagicNumber]` | AC-3 | manual | PASS |
| 7 | Запасной кейс: `fun classify(code: Int)` с `when` на 15 ветвей + `else` → `:detekt FAILED`, `e: …:13:5 The function classify appears to be too complex … (complexity: 17) … '14' [CyclomaticComplexMethod]` | AC-3 | manual | PASS |
| 8 | После кейса 3 (неиспользуемый импорт, main): `./gradlew ktlintFormat` → `BUILD SUCCESSFUL`, файл побайтно совпал с исходным (`cmp`) | AC-4 | manual | PASS |
| 9 | После кейса 2 (отступ, main): `./gradlew ktlintFormat` → файл побайтно совпал с исходным | AC-4 | manual | PASS |
| 10 | После кейса 5 (импорт, test): `./gradlew ktlintFormat` → файл побайтно совпал с исходным | AC-4, AC-5 | manual | PASS |
| 11 | `ForbiddenImport`, `./gradlew detekt`: в `BuildSmokeTest.kt` отдельный класс без Spring (`class SilentJUnitTest { @Test fun x() {…} }`), где используются `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.assertEquals`, `org.junit.jupiter.api.assertThrows` (Kotlin API), `org.assertj.core.api.Assertions.assertThat`, `org.hamcrest.MatcherAssert`, `org.hamcrest.Matchers.equalTo`, `kotlin.test.assertTrue` → `:detekt FAILED`, по одной находке `e: файл:строка:1 The import … has been forbidden: <reason из detekt.yml> [ForbiddenImport]` на **каждый** из 7 импортов | AC-6 фичи (ADR-0005), замечание QA по T01 | manual | PASS |
| 12 | `ForbiddenImport`, `./gradlew build --continue`: только `import org.junit.jupiter.api.Test` и `@Test` в классе без Spring → `:detekt FAILED` (`BuildSmokeTest.kt:5:1 … [ForbiddenImport]`), `BUILD FAILED` | AC-6 фичи (ADR-0005) | manual | PASS |
| 13 | `ForbiddenImport`: JUnit 4 `import org.junit.Test` и `import org.junit.jupiter.api.*` → обе находки `[ForbiddenImport]` (плюс `WildcardImport`) | AC-6 фичи (ADR-0005) | manual | PASS |
| 14 | `*.gradle.kts`: `version  = …` и `import java.util.UUID` в `build.gradle.kts` → `:ktlintKotlinScriptCheck FAILED`, `build.gradle.kts:4:1 Unused import (standard:no-unused-imports)` и `:15:9 Unnecessary long whitespace (standard:no-multi-spaces)` | AC-1 (п.2 плана: kts) | manual | PASS |
| 15 | `*.gradle.kts`: `rootProject.name  = …` в `settings.gradle.kts` → `:ktlintKotlinScriptCheck FAILED`, `settings.gradle.kts:1:18 … (standard:no-multi-spaces)` | AC-1 (п.2 плана: kts) | manual | PASS |
| 16 | conventions.md, «Сборка и проверки»: `./gradlew ktlintCheck detekt` выполняет только `ktlint*Check` и `detekt`, без `:test`; `ktlintFormat`, `detekt`, `detektGenerateConfig` существуют (`tasks --all`); отчёты лежат в `build/reports/ktlint/<задача>/<задача>.txt` и `build/reports/detekt/detekt.{html,xml,sarif,md}`; форматы консоли совпадают с фактическими (кейсы 2–7, 11): ktlint `файл:строка:колонка сообщение (standard:<правило>)`, detekt `e: файл:строка:колонка сообщение [<Правило>]`; `build --continue` при одновременных нарушениях ktlint и detekt показывает оба | AC-6 | manual | PASS |
| 17 | Чистый клон (временный git-репозиторий из файлов рабочей копии с учётом `.gitignore`, 94 файла, wrapper jar на месте → `git clone`): `./gradlew clean build` → `BUILD SUCCESSFUL`, все 4 проверки и 10/10 тестов; `gradlew.bat build` → `BUILD SUCCESSFUL` | AC-1, фича целиком | manual | PASS |
| 18 | Без PostgreSQL: в `runtimeClasspath` нет jdbc/postgres/flyway/hikari/r2dbc/jpa (0 совпадений); jar из клона запущен с `--server.port=18089` → `GET /actuator/health` 200 `{"groups":["liveness","readiness"],"status":"UP"}`, неизвестный путь → 404; тест `context loads without database` PASSED. Локальная служба PostgreSQL не останавливалась: приложение к БД не обращается | фича (Q2), AC T02 | manual | PASS |
| 19 | Исследовательский: JUnit без импорта, через полные имена (`@org.junit.jupiter.api.Test fun x() { org.junit.jupiter.api.Assertions.assertEquals(1, 2) }` в классе без Spring) → `./gradlew build` **зелёный**, тест молча не выполняется (движок только `kotest`) | вне AC (ADR-0005) | manual | Наблюдение (см. ниже) |

- Вердикт: **DONE**. AC-1..AC-6 подтверждены, дефектов против AC нет.
- Найденные дефекты (шаги, ожидаемо, фактически): нет.
- Наблюдения (не дефекты T03, на усмотрение architect / tech-lead):
  1. [minor] → architect (ADR-0005). Контроль «только Kotest» срабатывает только на импорты. Шаги: добавить в `BuildSmokeTest.kt` класс
     `class SilentJUnitTest { @org.junit.jupiter.api.Test fun x() { org.junit.jupiter.api.Assertions.assertEquals(1, 2) } }`, выполнить
     `./gradlew ktlintFormat build`. Ожидаемо по смыслу ADR-0005: сборка падает. Фактически: `BUILD SUCCESSFUL`, падающая проверка не исполняется.
     Это соответствует плану: п.4 и conventions.md («импорты запрещены `ForbiddenImport`») ограничивают контроль импортами. Обход пока ловит
     только код-ревью. Возможное решение (`ForbiddenAnnotation` / `ForbiddenMethodCall` detekt) скорее всего требует type resolution,
     так что вопрос того же рода, что `UnusedImport` в ADR-0004.
  2. [minor] → tech-lead (conventions.md, строка 55). «Удобно, когда ktlint падает раньше и скрывает находки detekt». На практике порядок
     обратный тоже бывает: при нарушениях обоих типов `build` упал на `:detekt` и не показал находку ktlint (кейс 16). Рецепт
     `build --continue` в той же строке покрывает оба случая, поэтому вывод команд документация не искажает. Можно уточнить: «одна проверка
     может скрыть находки другой → `--continue`».

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-23 | product-manager | — → DRAFT | Создана задача: проверки стиля/качества в сборке, AC-1..AC-6; объём зависит от ответа на Q3 |
| 2026-10-23 | architect | DRAFT → PLAN_REVIEW | Технический план: ktlint 1.8.0 (ktlint-gradle 14.2.0) + detekt 2.0.0-alpha.3 (ADR-0004); Q3 подтверждён пользователем. «Зависит от» изменено: T01 → T01, T02 — T02/T03 правят одни файлы сборки, а проверки должны проходить на коде каркаса Spring Boot (AC-1) |
| 2026-10-23 | tech-lead | PLAN_REVIEW → NEEDS_REWORK | Ревью плана → architect: 1 major (пример MagicNumber для AC-3 не срабатывает в дефолтах alpha.3), 2 minor (failOnSeverity явно; фоллбек на alpha.6 не подтверждён совместимостью). API detekt alpha.3 и дефолтный конфиг сверены по jar |
| 2026-10-23 | architect | NEEDS_REWORK → PLAN_REVIEW | Исправлено по ревью: [major] пример AC-3 заменён на `fun timeoutMillis(): Long = 37L * 1000` (число в выражении, не в объявлении) + альтернатива `CyclomaticComplexMethod` > 14, перечислены неработающие варианты; [minor] явный `failOnSeverity = FailOnSeverity.Error` в п.2; [minor] фоллбек: новая alpha — только при проверенно зелёном `./gradlew build` на нашем стеке, иначе эскалация к architect. Синхронно обновлены ADR-0004 и architecture.md. detekt 2.0.0-alpha.3 подтверждён пользователем |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Повторное ревью плана (круг 2): все замечания круга 1 (1 major, 2 minor) исправлены; план согласован с ADR-0004, architecture.md, conventions.md. Новых замечаний нет |
| 2026-10-23 | architect | READY → PLAN_REVIEW | Сверка с Kotest (ADR-0005): тесты — `FunSpec` со строковыми именами, правила именования функций ktlint/detekt на тесты не влияют. План изменён: в `detekt.yml` добавлен `style>ForbiddenImport` (`org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*`; формат `forbiddenImports` value/reason сверен по jar detekt 2.0.0-alpha.3) — автоматический контроль «тестов на JUnit нет»; добавлен кейс проверки; AC-5 уточнён под Kotest-спецификацию. AC задачи не менялись. Требуется повторное ревью плана |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Ревью плана (круг 3, Kotest): конфиг `ForbiddenImport` сверен по jar detekt 2.0.0-alpha.3 (ключи, glob → regex, отсутствие excludes для тестов), нужные импорты T01/T02 не задеваются, транзитивные зависимости не влияют; 1 minor (негативный кейс `ForbiddenImport` проверять через `./gradlew detekt`) |
| 2026-10-23 | architect | READY (без изменений) | Решение пользователя (2026-10-23, Q8 в feature.md) о запасном варианте T02 (Spring-тесты на JUnit Jupiter при неработоспособности `kotest-extensions-spring`): в «Риски» добавлено, как меняется T03 в этом случае — `org.junit.*` в `ForbiddenImport` заменяется точечными шаблонами (JUnit `Assertions`/`Assumptions`, JUnit 4), `org.junit.jupiter.api.Test` разрешён, негативная проверка — через импорт `Assertions.assertEquals`/AssertJ. Основной план и AC не менялись |
| 2026-10-23 | tech-lead | READY → NEEDS_REWORK | Ревью запасного варианта ADR-0005 (круг 4): `ForbiddenImport` сверен по байткоду detekt 2.0.0-alpha.3 (полное совпадение по FqName, `allowedImports`). 1 major: точечные шаблоны пропускают Kotlin-проверки JUnit (`org.junit.jupiter.api.assertThrows` и т.п.), `import org.junit.jupiter.api.*`, `@Disabled` → заменить на `org.junit.*` + `allowedImports: [org.junit.jupiter.api.Test]`; 1 minor (негативная/позитивная проверка). → architect |
| 2026-10-23 | architect | NEEDS_REWORK → PLAN_REVIEW | Круг 4 исправлен (только блок «Если в T02 применён запасной вариант ADR-0005»): [major] точечный blacklist заменён белым списком — `forbiddenImports` как в п.4 (`org.junit.*`, `org.assertj.*`, `org.hamcrest.*`, `kotlin.test.*`) + `allowedImports: ['org.junit.jupiter.api.Test']`; [minor] в негативную проверку добавлен `import org.junit.jupiter.api.assertThrows`, добавлена позитивная проверка (Spring-тесты с `org.junit.jupiter.api.Test` проходят `detekt`). Синхронно обновлены ADR-0005 (п.6 «Решения», таблица, «Обязательные правки документов») и architecture.md («Риски»). Основной план и AC не менялись |
| 2026-10-23 | tech-lead | PLAN_REVIEW → READY | Повторное ревью (круг 5) запасного варианта ADR-0005: major закрыт (белый список `allowedImports: ['org.junit.jupiter.api.Test']` при неизменных `forbiddenImports`), оба minor закрыты (негативные и позитивная проверки в T03, перечень правок в ADR-0005 и T02). T03, T02, ADR-0005, architecture.md и feature.md (Q8) согласованы. Новых замечаний нет |
| 2026-10-23 | kotlin-backend-developer | READY → IN_PROGRESS | Взята в работу: ktlint + detekt по плану (основной вариант ADR-0005, без `allowedImports`) |
| 2026-10-24 | kotlin-backend-developer | IN_PROGRESS → IN_REVIEW | ktlint 1.8.0 + detekt 2.0.0-alpha.3 подключены к `check` (каталог, build.gradle.kts, .editorconfig, detekt.yml с `ForbiddenImport`), существующие тесты отформатированы `ktlintFormat`, в conventions.md добавлен раздел «Сборка и проверки». `clean build` зелёный (10/10 тестов). Негативные проверки AC-2/3/5 и ForbiddenImport (JUnit `@Test` в не-Spring классе) падают с файл:строка:правило. Отклонения: `asProvider()`, `verbose = true`; неиспользуемые импорты по умолчанию не ловятся, вопрос к architect |
| 2026-10-24 | tech-lead | IN_REVIEW → IN_TESTING | Ревью кода: `clean build` зелёный (ktlint main/test/kts + detekt, 10/10 PASSED); план соблюдён, подавлений нет; переформатирование тестов логику не изменило (сверены все 5 спек). Отклонения `asProvider()` и `verbose = true` приняты. Неиспользуемые импорты принято с оговоркой: временными правками проверено, что detekt `UnusedImport` в alpha.3 требует type resolution (`@RequiresAnalysisApi`) и в `detekt` не работает, а ktlint `ktlint_standard_no-unused-imports = enabled` работает и автоисправляется → решение за architect (ADR-0004, «Как проверить» AC-2). 0 blocker / 0 major / 3 minor (1 → architect, 2 — уточнения conventions.md) |
| 2026-10-24 | architect | IN_TESTING (без изменений) | Решение по [minor] ревью кода (неиспользуемые импорты): принята рекомендация tech-lead — `ktlint_standard_no-unused-imports = enabled` с комментарием «пересмотреть при ktlint 2.0» в `[*.{kt,kts}]` `.editorconfig`. Отклонены: detekt `UnusedImport` (нужен type resolution, т.е. `detektMain`/`detektTest` в `check`) и ручной контроль. Зафиксировано в ADR-0004 («Решение» п.1, «Последствия», «Альтернативы»). В плане обновлены п.3 (`.editorconfig`), «Ключевые решения» и «Как проверить» AC-2 (отдельный кейс неиспользуемого импорта, связь с AC-4). AC не менялись. Перед QA разработчик должен добавить строку в `.editorconfig`; conventions.md (строки 58, 62–63) уточняет tech-lead |
| 2026-10-24 | kotlin-backend-developer | IN_TESTING → IN_PROGRESS | Доработка по решению architect (ADR-0004): в `.editorconfig` включается `ktlint_standard_no-unused-imports`. Это не круг CHANGES_REQUESTED |
| 2026-10-24 | kotlin-backend-developer | IN_PROGRESS → IN_REVIEW | Включён `ktlint_standard_no-unused-imports = enabled` в `.editorconfig` (ADR-0004). `clean build` зелёный (10/10). Негатив: `import java.util.UUID` в main → `:ktlintMainSourceSetCheck FAILED` с `(standard:no-unused-imports)`, `ktlintFormat` удаляет импорт. Временная правка откачена, итоговый `clean build` зелёный |
| 2026-10-24 | tech-lead | IN_REVIEW → IN_TESTING | Повторное ревью «Дополнения (2026-10-24)»: `ktlint_standard_no-unused-imports = enabled` в `.editorconfig` соответствует ADR-0004 и п.3 плана; `clean build` зелёный; на тестовом коде неиспользуемый импорт роняет `ktlintTestSourceSetCheck`, а `ktlintFormat` его удаляет. Закрыты оба minor по conventions.md («Сборка и проверки»: форматы вывода ktlint/detekt; неиспользуемые импорты контролирует ktlint, detekt `UnusedImport` требует type resolution). Открытых замечаний нет |
| 2026-10-24 | qa-engineer | IN_TESTING → DONE | 19 кейсов (manual), автотесты не добавлялись (проверяется поведение сборки). AC-1..AC-6 подтверждены: отступ и неиспользуемый импорт в main/test/`*.gradle.kts` роняют ktlint, MagicNumber и CyclomaticComplexMethod роняют detekt, `ktlintFormat` побайтно восстанавливает файлы, `ForbiddenImport` ловит JUnit (`@Test` в классе без Spring, `Assertions`, `assertThrows`, JUnit 4, `*`), AssertJ, Hamcrest, `kotlin.test` через `detekt` и `build --continue`, conventions.md соответствует фактическому поведению. Чистый клон: `clean build` и `gradlew.bat build` зелёные, БД не нужна. Дефектов нет; 2 minor-наблюдения (обход `ForbiddenImport` через полные имена → architect; уточнение строки 55 conventions.md → tech-lead) |
