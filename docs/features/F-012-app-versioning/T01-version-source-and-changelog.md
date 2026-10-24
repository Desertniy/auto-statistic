# F-012/T01: version.properties, CHANGELOG и версия в имени jar

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | IN_TESTING                       |
| Фича        | [F-012](./feature.md)            |
| Тип         | tech                             |
| Зависит от  | —                                |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Завести в корне репозитория два файла и сделать `version.properties` единственным источником версии сборки.

1. **`version.properties`** — текущая версия приложения в формате `X.Y.Z`. Стартовое значение — `1.0.2`
   (версия последней выполненной задачи F-001/T03, ответ пользователя на Q3a).
2. **`CHANGELOG.md`** — журнал изменений: одна строка на одну выполненную задачу в формате
   `<номер версии> <номер задачи> <описание задачи>`; новые строки вверху; описание — название задачи из
   заголовка её файла. В начале файла — краткое пояснение формата и ссылка на `docs/WORKFLOW.md`.
   Стартовое содержимое — ретроспективные записи F-001 (ответы пользователя на Q3, Q3a):
   ```
   1.0.2 F-001/T03 Автоматические проверки качества кода в сборке
   1.0.1 F-001/T02 Каркас Spring Boot-приложения с проверкой работоспособности
   1.0.0 F-001/T01 Сборка Kotlin-проекта на Gradle
   ```
3. **Имя jar.** Сборка (`./gradlew build`) берёт версию из `version.properties`, и версия попадает в имя собранного
   jar приложения (минимальное требование «видно в проде»). Шаблонная `0.0.1-SNAPSHOT` больше нигде не используется.

Поднятие версии за саму эту задачу (`1.0.2` → `1.0.3`, строка `1.0.3 F-012/T01 ...`) в реализацию не входит:
его делает оркестратор отдельным коммитом в ветке задачи после согласия пользователя на слияние (решение Q2).

## Критерии приёмки
- [ ] AC-1: Given ветка задачи, When открыть корень репозитория, Then есть `version.properties` с версией `1.0.2`
  и `CHANGELOG.md`, в начале которого описан формат записи `<версия> <F-NNN/TNN> <описание>`.
- [ ] AC-2: Given `CHANGELOG.md`, When прочитать записи, Then есть ровно три записи, по одной на F-001/T01, T02, T03,
  с версиями `1.0.0`, `1.0.1`, `1.0.2` соответственно, упорядоченные от новой к старой; описания дословно совпадают
  с названиями задач в заголовках файлов `docs/features/F-001-project-bootstrap/T0N-*.md`; каждая запись — одна строка
  вида «версия, пробел, id задачи, пробел, описание».
- [ ] AC-3: Given `CHANGELOG.md` и `version.properties`, When сравнить, Then версия в `version.properties` равна версии
  самой свежей записи `CHANGELOG.md`.
- [ ] AC-4: Given `version.properties` = `1.0.2`, When выполнить `./gradlew build` (и `gradlew.bat build` на Windows),
  Then сборка зелёная, а в каталоге артефактов есть исполняемый jar приложения, имя которого содержит `1.0.2`
  (например, `auto-statistic-1.0.2.jar`; точный шаблон имени — по плану architect).
- [ ] AC-5: Given jar из AC-4, When запустить его (`java -jar <jar>`), Then приложение стартует и
  `GET /actuator/health` отвечает `200` `{"status":"UP"}`.
- [ ] AC-6: Given в `version.properties` вручную поставлено `1.0.7`, When пересобрать, Then имя jar содержит `1.0.7`
  (и не содержит прежнюю версию); других файлов для смены версии править не нужно.
- [ ] AC-7: Given поиск по репозиторию (исходники, конфигурация, файлы сборки), When искать `0.0.1-SNAPSHOT` и
  захардкоженные номера версии приложения, Then их нет — версия задаётся только в `version.properties`.
- [ ] AC-8 (негативный): Given `version.properties` отсутствует, или ключ версии пуст, или значение не в формате
  `X.Y.Z` (например, `1.0`, `1.0.x`, `v1.0.0`, `1.0.0-SNAPSHOT`), When выполнить `./gradlew build`, Then сборка падает
  с понятным сообщением, что версия в `version.properties` отсутствует/некорректна (Допущение, Q10 фичи).
- [ ] AC-9 (граничный): Given значения `0.0.0` и `10.20.300`, When выполнить сборку, Then они принимаются как
  корректные и попадают в имя jar.
- [ ] AC-10: Given изменения задачи, When выполнить `./gradlew build`, Then существующие проверки (тесты Kotest,
  ktlint, detekt) проходят.

## Открытые вопросы
- Q8 фичи (без `-SNAPSHOT`) и Q10 (сборка падает на некорректной версии) — приняты допущения.

<!-- ============ architect ============ -->
## Технический план
- **Предусловие:** рабочее дерево чистое, планировочные документы F-012 и правки раздела 7 `WORKFLOW.md` закоммичены
  в `main` (решает пользователь). Ветка `F-012/T01` — от `main`.
- **Модули / пакеты:** код приложения (`src/`) не меняется. Файлы:
  - `version.properties` (новый, корень, ASCII, LF) — содержимое ровно по [architecture.md](./architecture.md), «Модель данных»:
    две строки-комментария на английском + `version=1.0.2`.
  - `CHANGELOG.md` (новый, корень, UTF-8, LF) — заголовок `# Changelog`, абзац-пояснение формата со ссылкой на
    `docs/WORKFLOW.md` (раздел 7), затем три записи F-001 (`1.0.2` → `1.0.1` → `1.0.0`), между записями пустая строка.
    Текст — по architecture.md; названия задач — дословно из заголовков `docs/features/F-001-project-bootstrap/T0N-*.md`.
    Записи `1.0.3 F-012/T01 …` **не добавлять** — это шаг оркестратора при слиянии.
  - `build.gradle.kts`: удалить `version = "0.0.1-SNAPSHOT"`; вместо него
    `version = parseAppVersion(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.orNull)`
    на том же месте (после `group`, до прочих блоков). В конце скрипта (функции скрипта видны до объявления; если
    компилятор скрипта воспротивится — перед первым использованием) — top-level функция
    `fun parseAppVersion(propertiesText: String?): String`:
    `null` → `GradleException` «not found»; `Properties().apply { load(StringReader(text)) }.getProperty("version")?.trim()`
    пустое/`null` → `GradleException` «missing or empty»; не соответствует regex
    `^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$` → `GradleException` «invalid version '<v>'»; иначе — значение.
    Regex — `val`/константа в скрипте с говорящим именем; тексты сообщений — по таблице в architecture.md
    (английский, с именем файла, проблемой и ожидаемым форматом). Импорты (`java.util.Properties`, `java.io.StringReader`)
    — в шапке скрипта, ktlint `ktlint_official` (включая `*.gradle.kts`) должен пройти.
  - Не читать файл через `java.io.File`/`file(...).readText()` и не использовать `afterEvaluate` — только
    `providers.fileContents` (вход configuration cache).
  - Имя jar не настраивать вручную: конвенция Boot даёт `build/libs/auto-statistic-<версия>.jar` (исполняемый) и
    `auto-statistic-<версия>-plain.jar`; `archiveVersion`/`archiveFileName` не трогать.
  - Документы: ADR-0002 и `overview.md` уже обновлены architect; в `src/` и конфигурации других упоминаний версии нет.
- **API-контракт:** не меняется.
- **Модель данных / миграции:** БД нет. Формат файлов — architecture.md, «Модель данных».
- **Доменные типы и ошибки:** не вводятся (версия — артефакт сборки). Ошибки — `GradleException` на фазе конфигурации.
- **Ключевые решения:** [ADR-0006](../../architecture/adr/ADR-0006-app-versioning.md) п. 1–3;
  [architecture.md](./architecture.md) «Поток 1». Без `buildSrc`, без плагинов версионирования, без `-SNAPSHOT` (Q8).
- **Тесты / проверка:** новых автотестов нет (логика в build-скрипте; TestKit отклонён, см. architecture.md
  «Альтернативы»); существующие Kotest/ktlint/detekt — зелёные (AC-10). Проверка QA (manual, временные правки не коммитить):
  - AC-4/AC-5: `./gradlew clean build` → `build/libs/auto-statistic-1.0.2.jar`; `java -jar … --server.port=18082` →
    `GET /actuator/health` = `200 {"status":"UP"}`; то же через `gradlew.bat clean build`.
  - AC-6/AC-9: `1.0.7`, `0.0.0`, `10.20.300` → `clean build` → jar с этой версией (без `clean` старые jar остаются в
    `build/libs` — штатно, проверять по новому файлу).
  - AC-8: нет файла; `version=`; нет ключа; `1.0`, `1.0.x`, `v1.0.0`, `1.0.0-SNAPSHOT`, `01.0.0` → `BUILD FAILED` с
    сообщением о `version.properties`.
  - AC-7: `git grep -n "0.0.1-SNAPSHOT"` — совпадения только в истории документов F-001 (`docs/features/F-001-*`) и в
    ADR-0002 как упоминание прежнего значения; в `*.kts`, `src/`, `gradle/` — нет.
  - Configuration cache: `./gradlew help --configuration-cache` дважды (reuse), смена версии → cache не переиспользуется
    из-за изменения `version.properties`.
- **Риски:** `asText` читает файл в кодировке по умолчанию — поэтому `version.properties` строго ASCII;
  ktlint на `build.gradle.kts` (длина строки 120, порядок импортов) — прогнать `ktlintFormat`/`ktlintCheck`.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY** (с одним обязательным уточнением реализации, п. 1)
- Проверено экспериментом (временная копия проекта вне репозитория, 2026-10-24, Gradle 9.3.1): чтение через
  `providers.fileContents(...).asText.orNull` работает (нет файла → `null`); `./gradlew help --configuration-cache`
  при повторном запуске — «Configuration cache entry reused», после правки версии — «cannot be reused because file
  'version.properties' has changed»; все негативные значения из AC-8 роняют конфигурацию с `GradleException`, `1.0.2 `
  (хвостовой пробел) принимается; `ktlintKotlinScriptCheck` проходит для скрипта с top-level функцией и импортами
  `java.io.StringReader`/`java.util.Properties`; `bootJar`/`jar` дают `auto-statistic-1.0.2.jar` и `-plain.jar`.
- Замечания:
  1. **[обязательно] Порядок инициализации в скрипте.** План допускает «regex — `val`/константа в скрипте» и функцию
     в конце скрипта. Функция, объявленная ниже, вызывается корректно, но **top-level `val` скрипта, объявленный ниже
     строки `version = parseAppVersion(...)`, на момент вызова ещё `null`** — проверено: `Cannot invoke
     "kotlin.text.Regex.matches(...)" because "this.semVerCore" is null`, сборка падает при валидной версии.
     → Regex — локальный `val` внутри `parseAppVersion` (рекомендуется) либо top-level `val` строго **выше** строки
     `version = ...`. `const val` для `Regex` неприменим. Обязательная проверка разработчика: `./gradlew help` с
     `version=1.0.2` зелёный (не только негативные сценарии).
  2. [minor] AC-7 / проверка QA: список допустимых совпадений `0.0.1-SNAPSHOT` неполный — строка также есть в
     документах F-012 (`architecture.md`, задачи), ADR-0006 и заметке ADR-0002. → Критерий для QA: совпадений нет в
     `*.kts`, `*.properties`, `*.yaml`, `src/`, `gradle/`, `config/`; в `docs/` — допустимы как исторические упоминания.
  3. [minor] `CHANGELOG.md` и комментарий `version.properties` ссылаются на раздел 7 `WORKFLOW.md`, где правило
     появится только с T03. Принимается (порядок T01 → T03 подтверждён), переписывать ссылку при слиянии T03 не нужно.
  4. [minor] Сообщения об ошибке: дословно эталон из architecture.md не обязателен, но каждое должно содержать имя
     файла, проблему, ожидаемый формат **и пример** (`e.g. 1.0.2`) — так понятнее при правке руками.
  5. Формат `CHANGELOG.md` (пустая строка между записями, новые сверху, regex записи) и имя jar по конвенции Boot —
     согласованы с ADR-0006 и AC фичи; замечаний нет. Ручная проверка негативных сценариев без TestKit — принимается
     (детерминированная логика ~10 строк, проверена QA по таблице architecture.md).

<!-- ============ kotlin-backend-developer ============ -->
## Реализация
- **Ветка:** `F-012/T01` (от `main` @ `a130975`).
- **Изменённые файлы:**
  - `version.properties` (новый; ASCII, LF) — два комментария + `version=1.0.2`, дословно по architecture.md.
  - `CHANGELOG.md` (новый; UTF-8, LF) — `# Changelog`, абзац о формате со ссылкой на `docs/WORKFLOW.md` (раздел 7),
    три записи F-001 (`1.0.2` → `1.0.1` → `1.0.0`) через пустую строку; названия дословно из заголовков
    `docs/features/F-001-project-bootstrap/T0N-*.md`. Запись `1.0.3 F-012/T01` не добавлена (шаг оркестратора).
  - `build.gradle.kts` — литерал `0.0.1-SNAPSHOT` заменён на
    `version = parseAppVersion(providers.fileContents(layout.projectDirectory.file("version.properties")).asText.orNull)`
    (на прежнем месте, после `group`); импорты `java.io.StringReader`, `java.util.Properties`; в конце скрипта —
    top-level `fun parseAppVersion(propertiesText: String?): String` с KDoc.
  - Документы: статус задачи; `feature.md` (фича → `IN_PROGRESS`, T01 → `IN_REVIEW` в таблице задач, строка журнала);
    `docs/features/README.md` (F-012 → `IN_PROGRESS`).
- **Что сделано:**
  - `parseAppVersion`: `null` (нет файла) → `GradleException` «not found»; `Properties.load(StringReader)` →
    `getProperty("version")?.trim()`; `null`/пусто → «missing or empty»; несоответствие SemVer-ядру → «invalid version '<v>'».
    Каждое сообщение называет файл, проблему, ожидаемый формат и пример `1.0.2` (minor 4 ревью плана).
  - **Обязательное уточнение ревью плана (п. 1):** regex `semVerCore` — локальный `val` внутри `parseAppVersion`
    (не top-level `val` ниже вызова); проверено `./gradlew help -q` с `version=1.0.2` — зелёный, `version: 1.0.2`.
  - Чтение только через `providers.fileContents` (без `File`/`readText`/`afterEvaluate`); `archiveVersion`/`archiveFileName`
    не трогались — имя jar по конвенции Boot.
- **Тесты:** новых автотестов нет (по плану: логика в build-скрипте, TestKit отклонён). Запуск: `./gradlew clean build`
  (Windows: `gradlew.bat clean build`). Результаты прогона 2026-10-24:
  - AC-4/AC-10: `./gradlew clean build` — `BUILD SUCCESSFUL`, 10/10 тестов Kotest PASSED, `ktlintMainSourceSetCheck`,
    `ktlintTestSourceSetCheck`, `ktlintKotlinScriptCheck`, `detekt` — зелёные; `build/libs`: `auto-statistic-1.0.2.jar`,
    `auto-statistic-1.0.2-plain.jar`. `gradlew.bat clean build` — `BUILD SUCCESSFUL`, те же jar.
  - AC-5: `java -jar build/libs/auto-statistic-1.0.2.jar --server.port=18082` → `GET /actuator/health` = `200`,
    `{"groups":["liveness","readiness"],"status":"UP"}` (поле `groups` — штатное поведение F-001, без изменений).
  - AC-6/AC-9: `version=1.0.7` / `0.0.0` / `10.20.300` → `clean bootJar jar` → `auto-statistic-<v>.jar` и `-plain.jar`
    только с новой версией; `1.0.2 ` (хвостовой пробел) → `1.0.2`.
  - AC-8: нет файла; `version=`; `version=   `; нет ключа; `1.0`, `1.0.x`, `v1.0.0`, `1.0.0-SNAPSHOT`, `01.0.0`,
    `1.0.0.0`, `1.-1.0` → exit 1 с соответствующим сообщением о `version.properties` (проверено через `help -q`, для
    `1.0` — и через `./gradlew build`: `BUILD FAILED`). Временные правки откачены, `version.properties` = `1.0.2`.
  - AC-7: `git grep -n "0.0.1-SNAPSHOT" -- '*.kts' '*.properties' '*.yaml' '*.yml' src gradle config` — пусто;
    `git grep -nE 'version\s*=\s*"[0-9]' -- '*.kts' src` — пусто; в `docs/` — только исторические упоминания (minor 2).
  - Configuration cache: `help --configuration-cache` дважды — «Configuration cache entry reused»; после правки версии —
    «cannot be reused because file 'version.properties' has changed».
  - AC-1/AC-2/AC-3: проверены чтением файлов (верхняя запись `CHANGELOG.md` = `1.0.2` = `version.properties`).
- **Отклонения от плана:** нет. Поле «Ветка» в шапку задачи не добавлено — `docs/WORKFLOW.md` §7: «отдельное поле в
  файле задачи не ведётся — имя ветки равно id задачи».

<!-- ============ tech-lead ============ -->
## Ревью кода
- Вердикт: **IN_TESTING** (круг 1; blocker 0, major 0, minor 2)
- База: `main` @ `a130975`; `git log main..F-012/T01` — один коммит `b825d90`; всё, что относится к задаче, закоммичено.
- Проверено ревьюером (2026-10-24):
  - `./gradlew clean build` — `BUILD SUCCESSFUL` (10 тестов Kotest PASSED, `ktlint*Check` включая `ktlintKotlinScriptCheck`,
    `detekt`); `build/libs`: `auto-statistic-1.0.2.jar`, `auto-statistic-1.0.2-plain.jar` (AC-4, AC-10).
  - Обязательное уточнение ревью плана п. 1 выполнено: regex `semVerCore` — локальный `val` внутри `parseAppVersion`;
    валидная версия `1.0.2` разбирается (`build`, `help` зелёные).
  - `help -q` с временными значениями: `1.0`, `v1.0.0`, `1.0.0-SNAPSHOT`, `01.0.0` → «invalid version '<v>'»; `version=`
    и файл без ключа → «missing or empty»; нет файла → «not found»; все — `BUILD FAILED` (AC-8). `0.0.0`, `10.20.300` →
    `project.version` с этим значением (AC-9). Сообщения содержат имя файла, проблему, формат и пример (minor 4 плана).
  - Configuration cache: `help --configuration-cache` — stored → reused; после правки версии — «cannot be reused because
    file 'version.properties' has changed». Чтение только через `providers.fileContents`, без `File`/`afterEvaluate`.
  - AC-7: `git grep "0.0.1-SNAPSHOT"` по `*.kts`, `*.properties`, `*.yaml`, `*.yml`, `src`, `gradle`, `config` — пусто;
    литералов версии в `*.kts`/`src` нет.
  - AC-1–AC-3: `version.properties` — ASCII, LF, `version=1.0.2`; `CHANGELOG.md` — UTF-8, LF, текст по architecture.md,
    3 записи F-001 дословно по заголовкам файлов задач, верхняя `1.0.2` = `version.properties`. Записи `1.0.3` нет —
    штатно (шаг оркестратора при слиянии).
  - Временные правки `version.properties` откачены, рабочее дерево чистое.
- Замечания (`[blocker]` / `[major]` / `[minor]`, файл:строка):
  - [minor] `build.gradle.kts:79` — локальная `val version` внутри `parseAppVersion` затеняет `Project.version` скрипта;
    работает корректно, но при чтении легко спутать. → При следующей правке скрипта (T02) переименовать в `rawVersion`/`value`;
    в этой задаче править не обязательно.
  - [minor] `docs/features/F-012-app-versioning/feature.md:144` — в журнале фичи нет строки о переходе T01 в `IN_REVIEW`
    (в таблице задач статус обновлён, в журнале задачи строка есть). → При смене статуса задачи добавлять строку и в
    журнал фичи; отдельной правки не требуется.

<!-- ============ qa-engineer ============ -->
## Тестирование
| Кейс | Проверяет AC | Тип (auto/manual) | Результат |
|------|--------------|-------------------|-----------|

- Вердикт: — (DONE / CHANGES_REQUESTED)
- Найденные дефекты (шаги, ожидаемо, фактически):

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-24 | product-manager | — → DRAFT | Задача создана: version.properties как единственный источник версии сборки, стартовый CHANGELOG (допущение Q3 (а)) |
| 2026-10-24 | product-manager | DRAFT (без изменений) | По ответам пользователя (Q1, Q3, Q4): задача сужена до «версия из version.properties в имени jar»; CHANGELOG вынесен в T02; стартовая версия — по допущению Q3a (б) `1.0.2`; добавлены AC на имя jar и запуск jar |
| 2026-10-24 | product-manager | DRAFT (без изменений) | Ответы пользователя Q2 (а), Q3a (б): CHANGELOG с ретроспективой F-001 возвращён в T01 (иначе при слиянии T01 по правилу Q2 некуда записать строку `1.0.3 F-012/T01`); стартовые значения зафиксированы: `version.properties` = `1.0.2`, записи F-001 = `1.0.0`/`1.0.1`/`1.0.2` |
| 2026-10-24 | architect | DRAFT → PLAN_REVIEW | Технический план: `version.properties` (ASCII, ключ `version`) читается через `providers.fileContents`, валидация SemVer-ядра в `build.gradle.kts` с `GradleException`; имя jar — конвенция Boot `auto-statistic-<v>.jar`; формат `CHANGELOG.md` (пустая строка между записями); ADR-0006, заметка в ADR-0002. AC не менялись |
| 2026-10-24 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY. Чтение через `providers.fileContents`, configuration cache (reuse + инвалидация), негативные сценарии и ktlint на `*.gradle.kts` проверены экспериментом во временной копии. Обязательно: regex — локальный `val` в функции или выше строки `version = ...` (top-level `val` ниже вызова = NPE, воспроизведено); 3 minor |
| 2026-10-24 | kotlin-backend-developer | READY → IN_PROGRESS | Ветка `F-012/T01` создана от `main` (`a130975`) |
| 2026-10-24 | kotlin-backend-developer | IN_PROGRESS → IN_REVIEW | `version.properties` (1.0.2), `CHANGELOG.md` (3 записи F-001), `parseAppVersion` в `build.gradle.kts` (regex — локальный `val`, сообщения с примером). `clean build` зелёный (10/10 тестов, ktlint, detekt) на `gradlew` и `gradlew.bat`; AC-5–AC-9 и configuration cache проверены вручную, временные правки откачены |
| 2026-10-24 | tech-lead | IN_REVIEW → IN_TESTING | Ревью кода, круг 1: blocker 0, major 0, minor 2. `clean build` зелёный, jar `auto-statistic-1.0.2.jar`; regex — локальный `val` (уточнение плана выполнено); негативные/граничные значения, сообщения и configuration cache (reuse + инвалидация) проверены вручную |
