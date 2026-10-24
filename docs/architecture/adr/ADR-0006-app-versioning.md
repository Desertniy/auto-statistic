# ADR-0006: Версия приложения — `version.properties` → `project.version` → имя jar и `build-info` → `GET /actuator/info`
- Статус: Accepted
- Дата: 2026-10-24
- Фича: F-012

## Контекст
У приложения нет осмысленной версии: в `build.gradle.kts` шаблонная `version = "0.0.1-SNAPSHOT"` (ADR-0002, п. 6).
Пользователь требует (F-012): файл `version.properties` в корне — единственный источник версии; каждая слитая в `main`
задача поднимает patch-версию SemVer и добавляет строку в `CHANGELOG.md`; версия видна в имени jar и в
`GET /actuator/info` без аутентификации. Сейчас по HTTP открыт только `health` (ADR-0003, п. 3).

Ограничения: Gradle 9.3.1 (совместимость с configuration cache, ленивые API), один модуль без `buildSrc` (ADR-0002),
ktlint проверяет `*.gradle.kts` (ADR-0004), Spring Boot 4.1.2 + Actuator (ADR-0003), тесты — Kotest (ADR-0005).

Проверено по документации Spring Boot 4.1.2 (2026-10-24): `springBoot { buildInfo() }` создаёт задачу `bootBuildInfo`,
пишет `META-INF/build-info.properties` в `build/bootBuildInfo` и подключает этот каталог к ресурсам `main`; значения по
умолчанию: `build.version` = версия проекта, `build.name` = имя проекта, `build.group`, `build.artifact` (базовое имя
`bootJar`), `build.time`; любое из них исключается через `excludes`. Actuator `BuildInfoContributor` (`build`) включён по
умолчанию при наличии файла; `git` — при наличии `git.properties`; `env`, `java`, `os`, `process`, `ssl` по умолчанию выключены.

## Решение
1. **Источник версии** — `version.properties` в корне репозитория, ключ `version`, значение — ядро SemVer `X.Y.Z`
   (три неотрицательных целых без ведущих нулей, без префикса `v` и суффиксов `-SNAPSHOT`/`-rc`). Файл только ASCII.
2. **Чтение в Gradle**: `build.gradle.kts` читает файл через
   `providers.fileContents(layout.projectDirectory.file("version.properties")).asText` (файл становится входом
   configuration cache: правка версии инвалидирует кэш), разбирает `java.util.Properties`, валидирует regex
   `^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$` и присваивает `project.version`. Нет файла / пустой ключ / неверный
   формат → `GradleException` с понятным сообщением на английском (кириллица в консоли Windows может исказиться) —
   сборка падает на фазе конфигурации. Логика — небольшой функцией прямо в `build.gradle.kts` (без `buildSrc`, ADR-0002).
3. **Имя jar** — стандартная конвенция плагина Spring Boot: `bootJar` → `build/libs/auto-statistic-<версия>.jar`
   (`archiveBaseName` = `rootProject.name`, `archiveVersion` = `project.version`). Plain-jar
   (`auto-statistic-<версия>-plain.jar`) остаётся как есть.
4. **Версия в рантайме** — `springBoot { buildInfo { excludes.set(setOf("time", "group", "artifact")) } }`:
   `build-info.properties` содержит только `build.name` и `build.version`. `time` исключён ещё и потому, что иначе
   `bootBuildInfo` никогда не up-to-date и сборка невоспроизводима. Собственный контроллер/`InfoContributor` не пишется.
5. **Actuator** (изменение п. 3 ADR-0003): по HTTP экспонируются **только** `health` и `info`
   (`management.endpoints.web.exposure.include: health,info`); `GET /actuator/info` →
   `{"build":{"name":"auto-statistic","version":"<версия>"}}`. Защита от утечки: `management.info.git.enabled: false`
   (явно, даже если `git.properties` когда-нибудь появится); `env/java/os/process/ssl` остаются выключенными по умолчанию;
   discovery-страница `GET /actuator` **отключается** (`management.endpoints.web.discovery.enabled: false` → `404`) —
   пересмотр, предусмотренный architecture.md F-001 при расширении exposure: у неё нет потребителей, а абсолютные
   `href` за обратным прокси без forwarded-заголовков раскрывают внутренний хост/порт.
6. **Поднятие версии — процесс, а не автоматика**: при слиянии каждой задачи `DONE` в `main` оркестратор отдельным
   коммитом в ветке задачи поднимает patch (`X.Y.Z` → `X.Y.Z+1`) и добавляет строку `<версия> <F-NNN/TNN> <название>`
   в начало `CHANGELOG.md`, затем `merge --no-ff`. Процедура и защиты — `docs/WORKFLOW.md`, раздел 7 (вводится F-012/T03;
   до этого — `docs/features/F-012-app-versioning/architecture.md`, «Поток 3»). Major/minor — только по явному решению пользователя.
   Остальные агенты эти два файла не меняют.

## Последствия
- Плюсы: одна точка правды для версии; ни версии, ни логики разбора в коде приложения нет; Actuator отдаёт версию без
  кода; сборка воспроизводима (нет `build.time`); совместимо с configuration cache.
- Минусы: проверка формата живёт в build-скрипте (detekt его не анализирует, unit-тестов нет — негативные сценарии
  проверяются вручную QA); каждое поднятие версии меняет classpath → перезапуск тестов и пересборка jar (ожидаемо).
- Строки `version.properties`/`CHANGELOG.md` меняются в каждой слитой задаче: если ветка задачи отстала от `main`
  по версии, слияние даст конфликт — правило процесса требует остановиться и спросить пользователя (см. WORKFLOW, раздел 7).
- `/actuator` больше не отвечает `200` (контрактом не был).
- `version.properties` и CHANGELOG не сверяются автоматически (out of scope F-012, Q10).

## Рассмотренные альтернативы
- **`version=` в `gradle.properties`** (Gradle сам ставит `project.version`): отклонено — пользователь явно требует
  отдельный `version.properties`; к тому же без валидации формата.
- **Плагины версионирования (axion-release, gradle-git-versioning, nebula.release) / версия из git-тегов**: отклонено —
  теги и автоматика out of scope; лишняя зависимость; версия должна читаться из файла.
- **Собственный `InfoContributor` или `info.app.version=@...@` через `processResources`/`expand`**: отклонено — больше кода
  и ещё одно место, где упоминается версия; `build-info` — штатный механизм Boot.
- **Только `@Value("\${...}")` / свой `GET /version`-контроллер**: отклонено — пользователь просил `/actuator/info`.
- **Валидация в `buildSrc`/convention-плагине с unit-тестами (TestKit)**: отклонено (YAGNI, ADR-0002) — одна функция на
  десяток строк; при росте логики сборки — отдельным решением.
- **Оставить discovery `/actuator` включённым**: отклонено, см. п. 5.
- **Суффикс `-SNAPSHOT` для веточных сборок**: отклонено (допущение Q8 F-012) — сборка несёт версию из файла как есть.
