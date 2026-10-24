# F-012/T02: Версия приложения в GET /actuator/info

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-012](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-012/T01                        |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Чтобы понимать, какая версия работает «в проде», запущенное приложение отдаёт свою версию по HTTP:
`GET /actuator/info` без аутентификации возвращает версию, с которой приложение было собрано
(значение `version.properties` на момент сборки, T01). Ответ пользователя на Q4 (2026-10-24).

Сейчас по HTTP открыт только `health`; дополнительно открывается только `info`. Остальные эндпоинты Actuator
по HTTP остаются закрытыми. В ответе `info` — только безобидные сведения о приложении (версия; имя приложения —
на усмотрение architect), без путей, хостов, переменных окружения и секретов.
Вывод версии в лог старта **не требуется** (ответ пользователя).

## Критерии приёмки
- [ ] AC-1: Given приложение собрано с `version.properties` = `1.0.2` и запущено (`./gradlew bootRun` или
  `java -jar`), When `GET /actuator/info` без аутентификации, Then ответ `200`, `Content-Type` — JSON, в теле есть
  версия приложения `1.0.2` (точное имя/путь поля — по плану architect, фиксируется в задаче).
- [ ] AC-2: Given версия в `version.properties` изменена на `1.0.5` и приложение пересобрано, When `GET /actuator/info`,
  Then в ответе `1.0.5`; никаких других правок для этого не нужно.
- [ ] AC-3: Given jar приложения, When сравнить версию в его имени и версию из `/actuator/info` запущенного из него
  приложения, Then они совпадают.
- [ ] AC-4 (безопасность): Given ответ `/actuator/info`, When просмотреть всё его содержимое, Then в нём нет путей
  файловой системы, имён пользователей/хостов, переменных окружения, секретов, сведений о git (если они не
  согласованы отдельно) — только версия и, возможно, имя приложения.
- [ ] AC-5 (негативный): Given запущенное приложение, When запросить по HTTP другие эндпоинты Actuator
  (например, `/actuator/env`, `/actuator/beans`, `/actuator/configprops`, `/actuator/loggers`), Then они по-прежнему
  недоступны (`404`), как до задачи.
- [ ] AC-6 (регресс): Given запущенное приложение, When `GET /actuator/health`, Then `200` `{"status":"UP"}`, как до задачи.
- [ ] AC-7: Given изменения задачи, When выполнить `./gradlew build`, Then автотесты (Kotest) проверяют наличие
  версии в `/actuator/info` и закрытость прочих эндпоинтов; сборка зелёная.

## Открытые вопросы
- (нет)

<!-- ============ architect ============ -->
## Технический план
- **Предусловие:** F-012/T01 слита в `main` (ветка `F-012/T02` — от `main`); по рекомендуемому порядку к этому
  моменту слита и T03.
- **Модули / пакеты:** бинов, контроллеров и `InfoContributor` не пишем — только конфигурация Boot.
  - `build.gradle.kts`:
    - `springBoot { buildInfo { excludes.set(setOf("time", "group", "artifact")) } }` — `build-info.properties` содержит
      только `build.name` (= `rootProject.name`) и `build.version` (= `project.version` из T01). `time` исключается и ради
      up-to-date/воспроизводимости.
    - в `tasks.test { … }` добавить `systemProperty("autostatistic.expected-version", version.toString())` — ожидаемая
      версия для теста берётся из того же `project.version`, в тестах литерала версии нет.
  - `src/main/resources/application.yaml` (блок `management`):
    `endpoints.web.exposure.include: health,info`, `endpoints.web.discovery.enabled: false`, `info.git.enabled: false`;
    `endpoint.health.show-details: never` без изменений. `env/java/os/process/ssl`-контрибьюторы не включать (по умолчанию выключены).
  - `src/test/kotlin/com/autostatistic/InfoEndpointTest.kt` (новый) — Kotest `FunSpec`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`
    + `@AutoConfigureRestTestClient` + `@ApplyExtension(SpringExtension::class)`, `RestTestClient` через конструктор,
    ответ — `exchange().returnResult(...)` + Kotest-матчеры (как `HealthEndpointTest`, conventions.md «Тесты»). Тесты:
    1. `info endpoint returns 200 JSON with application version` — `GET /actuator/info` с `Accept: application/json`:
       `200`, `contentType` совместим с `application/json`, `body["build"]["version"]` равно
       `System.getProperty("autostatistic.expected-version").shouldNotBeNull()` (AC-1, AC-2, AC-3).
    2. `info endpoint exposes only build name and version` — тело **целиком** равно
       `mapOf("build" to mapOf("name" to "auto-statistic", "version" to <expected>))` (AC-4: нет `time`, `group`,
       `artifact`, `git`, `env`, `java`, `os`, `process`).
    3. `info endpoint is available without authentication` — запрос без заголовков авторизации → `200` (явная фиксация AC-1).
    4. Для каждого пути из списка `/actuator/env`, `/actuator/beans`, `/actuator/configprops`, `/actuator/loggers`,
       `/actuator/metrics`, `/actuator/mappings`, `/actuator/threaddump`, `/actuator/heapdump`, `/actuator/shutdown`,
       `/actuator` — отдельный тест `actuator path <path> is not exposed` → `404` (AC-5). Генерация — обычным
       `listOf(...).forEach { test("…$it") { … } }` внутри `FunSpec`, без новых зависимостей (data-test-модулей не подключать).
  - `HealthEndpointTest.kt`: переименовать `actuator endpoints other than health are not exposed` →
    `actuator endpoints other than health and info are not exposed` (тело без изменений); остальные тесты — регресс AC-6.
- **API-контракт:** `GET /actuator/info` → `200`, `{"build":{"name":"auto-statistic","version":"X.Y.Z"}}`;
  `Content-Type` — `application/json` (при `Accept: application/json`) или `application/vnd.spring-boot.actuator.v3+json`.
  `GET /actuator` → `404` ProblemDetail (было `200`); прочие эндпоинты Actuator — `404`; `/actuator/health` — без изменений.
  Полностью — [architecture.md](./architecture.md), «API».
- **Модель данных / миграции:** нет. Генерируемый `build/bootBuildInfo/META-INF/build-info.properties` не коммитится (`build/` в `.gitignore`).
- **Доменные типы и ошибки:** не вводятся.
- **Ключевые решения:** [ADR-0006](../../architecture/adr/ADR-0006-app-versioning.md) п. 4–5 (изменяет п. 3 ADR-0003 —
  заметка уже внесена architect); discovery `/actuator` отключается — пересмотр, предусмотренный architecture.md F-001.
- **Проверка (manual, QA):** AC-3 — `./gradlew clean build`, `java -jar build/libs/auto-statistic-<v>.jar --server.port=18083`,
  `curl -s -H "Accept: application/json" localhost:18083/actuator/info` → версия совпадает с именем jar; AC-2 — временно
  `version=1.0.5`, `clean build`, тот же запрос → `1.0.5` (правку откатить); `curl -s localhost:18083/actuator` → 404.
- **Риски:** если `excludes` не убирает `group`/`artifact` или Boot добавляет иные ключи — тест 2 упадёт: разработчик
  не расширяет контракт сам, а ставит `BLOCKED` с фактическим телом ответа. Тест, запущенный в IDE не через Gradle, не
  получит системное свойство и упадёт на `shouldNotBeNull` — ожидаемо, эталон — `./gradlew build`.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Проверено экспериментом (временная копия проекта вне репозитория, 2026-10-24, Boot 4.1.2, Gradle 9.3.1,
  `--configuration-cache`): `springBoot { buildInfo { excludes.set(setOf("time", "group", "artifact")) } }` даёт
  `build-info.properties` ровно с `build.name=auto-statistic` и `build.version=1.0.2` — **риск по `group`/`artifact`
  снят**; `GET /actuator/info` с `Accept: application/json` → `200`, `Content-Type: application/json`, тело ровно
  `{build={name=auto-statistic, version=1.0.2}}` (сравнение тела целиком проходит); `/actuator`, `/actuator/env`,
  `/actuator/heapdump`, `/actuator/shutdown` → `404 application/problem+json`; системное свойство
  `autostatistic.expected-version` из `tasks.test` доступно в тесте. **Все тесты F-001/T02** (`HealthEndpointTest`,
  включая «actuator endpoints other than health are not exposed», `ServerPortOverrideTest`, `PortInUseTest`,
  `AutoStatisticApplicationTest`) с `exposure: health,info` и `discovery.enabled: false` — зелёные; ни один тест F-001
  не обращается к `/actuator`.
- Замечания:
  1. [minor] Тест 1: доступ `body["build"]["version"]` к вложенной `Map<String, Any?>` требует приведения типа — не
     через `as`, а `shouldBeInstanceOf<Map<*, *>>()` (Kotest-матчер, conventions «Тесты»). Допустимо объединить тесты 1
     и 2 (сравнение тела целиком уже проверяет версию), оставив проверку `status`/`Content-Type` отдельными строками.
  2. [minor] Тест 3 «available without authentication» в текущем проекте (нет Spring Security) дублирует тест 1. Оставить
     можно как фиксацию AC-1 на будущее (F-002/F-011 добавят security), но имя/комментарий должны пояснять, что это
     регресс-страховка на появление аутентификации.
  3. [minor] Тест на `/actuator/...` → `404`: дополнительно проверять `Content-Type` совместим с
     `application/problem+json` (как в `HealthEndpointTest`) — фиксирует, что 404 отдаёт штатный ProblemDetail, а не
     пустой ответ Actuator.
  4. Kotest-стиль (`FunSpec`, `listOf(...).forEach { test("actuator path $it is not exposed") }`, без новых
     зависимостей), `@ApplyExtension(SpringExtension::class)`, `RestTestClient` через конструктор — соответствует
     conventions. Падение теста при запуске из IDE без Gradle (нет системного свойства → `shouldNotBeNull`) — принимается,
     эталон — `./gradlew build`; разработчик фиксирует это в «Реализации».

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
| 2026-10-24 | product-manager | — → DRAFT | Задача создана: версия в логе старта и в /actuator/info (допущение Q4 фичи) |
| 2026-10-24 | product-manager | DRAFT → (упразднена) | Исключена по ответу пользователя на Q4 («минимум — имя jar») |
| 2026-10-24 | product-manager | — → DRAFT | Восстановлена по уточнению пользователя к Q4: версия в `GET /actuator/info` без аутентификации; лог старта исключён; прочие эндпоинты Actuator закрыты |
| 2026-10-24 | architect | DRAFT → PLAN_REVIEW | Технический план: `build-info` плагина Boot (excludes `time`,`group`,`artifact`) → `/actuator/info` = `{"build":{"name","version"}}`; exposure `health,info`, discovery `/actuator` выключен, `info.git` выключен; `InfoEndpointTest` (тело целиком, 404 на прочие пути), ожидаемая версия — системным свойством из `project.version`. ADR-0006, заметка в ADR-0003. AC не менялись; имя поля версии зафиксировано: `build.version` |
| 2026-10-24 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY. Экспериментом подтверждено: `excludes` убирает `group`/`artifact`, тело `/actuator/info` ровно `build.name`+`build.version`, `/actuator` и прочие → 404 problem+json, тесты F-001/T02 зелёные с `health,info` и discovery off. 3 minor по стилю тестов |
