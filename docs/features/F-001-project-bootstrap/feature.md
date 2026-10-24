# F-001: Базовый Gradle-проект

| Поле     | Значение                                   |
|----------|--------------------------------------------|
| Статус   | DONE                                       |
| Автор    | product-manager                            |
| Создана  | 2026-10-23                                 |
| Приоритет| Must                                       |

## Исходный запрос
> Необходимо создать базовый проект Gradle проект

Дополнение пользователя (2026-10-23, до начала разработки):
> Я бы ещё добавил, что нужно для тестов подключить kotest

## Проблема и цель
Сейчас репозиторий — IntelliJ-модуль (`auto-statistic.iml`, `src/Main.kt` с шаблонным «Hello, Kotlin»)
без системы сборки. Его нельзя собрать и протестировать из командной строки, а значит нельзя вести
разработку фич по пайплайну (`docs/WORKFLOW.md`): чек-лист ревью и QA опираются на `./gradlew build`.

**Цель:** получить воспроизводимо собираемый Kotlin-проект на Gradle, на котором можно разрабатывать
backend по целевому стеку из `docs/architecture/overview.md`.

**Признак успеха:** на чистом клоне репозитория `./gradlew build` (или `gradlew.bat build` на Windows)
завершается успешно без предварительной установки Gradle; приложение запускается одной командой;
проект открывается в IntelliJ IDEA как Gradle-проект.

## Пользовательские сценарии
- Как разработчик, я хочу собрать и протестировать проект одной командой, чтобы не зависеть от настроек IDE.
- Как разработчик, я хочу запустить приложение локально одной командой и убедиться, что оно живо,
  чтобы начинать разработку фич на готовом каркасе.
- Как участник команды (tech-lead / qa), я хочу, чтобы сборка автоматически проверяла стиль и
  качество кода, чтобы ревью опиралось на единые автоматические проверки.

## Объём
**Входит:**
- Сборка на Gradle (Kotlin DSL) с Gradle Wrapper, закоммиченным в репозиторий.
- Kotlin JVM-проект со стандартной раскладкой исходников и тестов; рабочий запуск тестов.
- **Kotest как тестовый фреймворк проекта** (дополнение пользователя): все тесты фичи, включая тесты
  Spring-контекста и HTTP, пишутся на Kotest и выполняются в составе `./gradlew build`.
  Допущение: Kotest используется и как раннер/стиль спецификаций, и как основная библиотека проверок
  (assertions); тесты в стиле JUnit Jupiter (`@Test`) в проекте не пишутся. Стиль спецификаций, модули Kotest,
  интеграция со Spring и совместимость версий — решение architect.
- Каркас Spring Boot-приложения, который стартует и отдаёт признак работоспособности
  (подтверждено пользователем, Q1).
- Подключение автоматических проверок стиля/качества кода к сборке, роняющих `build` (подтверждено, Q3).
- Удаление/замена артефактов прежнего не-Gradle проекта (шаблонный `src/Main.kt`, сборка в `out/`),
  обновление `.gitignore` под Gradle.

**Не входит (out of scope):**
- Подключение к PostgreSQL, миграции Flyway, Testcontainers — отдельная фича/задача, когда появится
  первая доменная сущность (подтверждено, Q2).
- Любая бизнес-функциональность (статистика и т.п.).
- CI-пайплайн (GitHub Actions и пр.), Docker-образ, деплой (подтверждено, Q4).
- Безопасность/аутентификация.
- Библиотеки моков (MockK и т.п.), property-based-тесты, отчёты о покрытии — не запрошены; подключаются, когда понадобятся
  (Допущение).

## Критерии приёмки фичи
- [x] AC-1: Given чистый клон репозитория и установленный JDK, When выполнить `./gradlew build`
  (Windows: `gradlew.bat build`), Then сборка завершается успешно без предварительно установленного Gradle,
  тесты запускаются и проходят.
- [x] AC-2: Given собранный проект, When запустить приложение стандартной командой Gradle, Then приложение
  стартует без ошибок и отвечает на запрос проверки работоспособности признаком «работает».
- [x] AC-3: Given в коде есть нарушение стиля/качества, When выполнить `./gradlew build`, Then сборка
  падает с понятным сообщением о нарушении; на текущем коде проекта проверки проходят.
- [x] AC-4: Given проект открыт в IntelliJ IDEA, When IDE импортирует проект, Then он распознаётся как
  Gradle-проект, без зависимостей от старой конфигурации модуля (`auto-statistic.iml`, библиотека `KotlinJavaRuntime`).
- [x] AC-5: Given выполнена сборка, When выполнить `git status`, Then артефакты сборки (`build/`, `.gradle/`)
  не попадают в список неотслеживаемых файлов.
- [x] AC-6: Given выполнена команда `./gradlew build`, When просмотреть тестовый код и отчёт о тестах, Then все тесты
  проекта (в т.ч. тесты подъёма Spring-контекста и HTTP-эндпоинтов) написаны на Kotest, реально выполняются в составе
  сборки и видны в отчёте; падение Kotest-теста роняет `build`; тестов в стиле JUnit Jupiter (`@Test`) нет.

Подтверждение (qa-engineer, 2026-10-24, по итогам T01–T03): AC-1 — чистый клон, `./gradlew clean build` и `gradlew.bat build` зелёные;
AC-2 — `./gradlew bootRun` → `/actuator/health` `{"status":"UP"}`; AC-3 — T03 (ktlint/detekt роняют `build` с файл:строка:правило);
AC-4 — по T01 (файлы старого модуля удалены, Gradle-импорт); сам импорт в IDE остаётся ручной проверкой пользователя;
AC-5 — `build/`, `.gradle/` в `git status --ignored` помечены как игнорируемые; AC-6 — 10/10 Kotest-тестов в отчёте, `@Test`/`org.junit` в `src/` нет.

## Нефункциональные требования
- Версии языка/платформы — по `docs/architecture/overview.md`: **JDK 17** (решение пользователя, Q5, ADR-0001),
  Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.3.1 (ADR-0002, ADR-0003).
- Сборка воспроизводима: версии Gradle и плагинов зафиксированы.
- Сборка работает на Windows (основная машина разработчика) и Unix-подобных ОС.

## Задачи
| ID  | Название | Статус | Зависит от |
|-----|----------|--------|------------|
| T01 | [Сборка Kotlin-проекта на Gradle](./T01-gradle-build-skeleton.md) | DONE | — |
| T02 | [Каркас Spring Boot-приложения с проверкой работоспособности](./T02-spring-boot-skeleton.md) | DONE | F-001/T01 |
| T03 | [Автоматические проверки качества кода в сборке](./T03-code-quality-checks.md) | DONE | F-001/T01, F-001/T02 |

Порядок реализации: T01 → T02 → T03 (см. [architecture.md](./architecture.md)).

## Открытые вопросы
Q1–Q8 закрыты (2026-10-23) ответами пользователя / решениями architect.
- [x] Q8: Что делать, если `kotest-extensions-spring` не заработает на Spring 7 / Boot 4.1.2? **Решение пользователя (2026-10-23):**
  Kotest остаётся основным; при реальной неработоспособности расширения (с зафиксированной ошибкой) разработчик в T02 переходит
  на JUnit Jupiter (BOM Boot) **без повторного согласования** с пользователем. **Решение architect:** заранее спроектированный
  запасной вариант — [ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md), раздел «Запасной вариант»: на JUnit Jupiter —
  только тесты с `@SpringBootTest`, все прочие тесты — Kotest `FunSpec`, проверки во всех тестах — Kotest-матчеры;
  `includeEngines("kotest", "junit-jupiter")`; detekt `ForbiddenImport` (T03) — белый список: из JUnit разрешён только импорт `org.junit.jupiter.api.Test`. Основной план и AC не меняются.
  Если запасной вариант применён, AC-6 фичи и AC-7 T02 проверяются в редакции этого решения: «все тесты на Kotest, тестов
  в стиле JUnit Jupiter нет» → «тесты без Spring-контекста — Kotest-спецификации; JUnit `@Test` — только в классах с
  `@SpringBootTest`; проверки во всех тестах — Kotest-матчеры». Остальная часть AC (тесты выполняются в сборке, видны в отчёте,
  падение роняет `build`) — без изменений.
- [x] Q7: «Подключить Kotest» — только раннер/стиль спецификаций или также Kotest-assertions как основная
  библиотека проверок? **Ответ пользователя (2026-10-23):** и раннер, и assertions; JUnit-style тесты (`@Test`) не пишутся
  (допущение PM подтверждено). **Решение architect:** Kotest 6.2.6, стиль `FunSpec`, `kotest-runner-junit6`,
  `kotest-extensions-spring` ([ADR-0005](../../architecture/adr/ADR-0005-kotest-testing.md)).
- [x] Для architect: план T03 сверен со стилем Kotest — дополнен detekt `ForbiddenImport` (контроль «только Kotest»), T03 → PLAN_REVIEW.
- [x] Q1: «Базовый проект» = только Gradle или сразу каркас Spring Boot? **Ответ пользователя:** Gradle + каркас
  Spring Boot с health-check; T02 остаётся.
- [x] Q2: Подключать PostgreSQL/Flyway/Testcontainers в этой фиче? **Ответ пользователя:** нет.
- [x] Q3: Нужны ли проверки стиля/качества сразу и должны ли ронять сборку? **Ответ пользователя:** да, сразу и
  ронят сборку; T03 остаётся. Инструменты выбрал architect: ktlint + detekt
  ([ADR-0004](../../architecture/adr/ADR-0004-code-quality-ktlint-detekt.md)).
- [x] Q4: Нужны ли CI и Docker-образ? **Ответ пользователя:** нет, вне фичи.
- [x] Q5: Какой JDK? **Ответ пользователя:** JDK 17 (как в IDE, Liberica 17), не 21. **Решение architect:** Gradle toolchain 17
  ([ADR-0001](../../architecture/adr/ADR-0001-jdk-17.md)); `overview.md` исправлен.
- [x] Q6: Базовый пакет и group? **Решение architect:** `group = com.autostatistic`, базовый пакет `com.autostatistic`,
  `rootProject.name = auto-statistic` ([ADR-0002](../../architecture/adr/ADR-0002-gradle-build.md)).

## Журнал
| Дата | Агент | Изменение статуса | Комментарий |
|------|-------|-------------------|-------------|
| 2026-10-23 | product-manager | — → DRAFT | Создана фича, декомпозиция на T01–T03, зафиксированы допущения и открытые вопросы Q1–Q6 |
| 2026-10-23 | architect | DRAFT → DESIGN | Закрыты Q1–Q6 по ответам пользователя; architecture.md, ADR-0001..0004; T01–T03 → PLAN_REVIEW; T03 теперь зависит от T02 (общие файлы сборки, проверки на коде каркаса); порядок T01 → T02 → T03 |
| 2026-10-23 | tech-lead | DESIGN (без изменений) | Ревью плана: T01, T02 → READY; T03 → NEEDS_REWORK (→ architect: пример MagicNumber для AC-3, failOnSeverity, фоллбек detekt). Фича остаётся DESIGN до READY всех задач; conventions.md дополнен разделом «Сборка и зависимости» (версии в каталоге, platform-BOM, связка Kotlin/Gradle/detekt, особенности Boot 4, политика подавлений), уточнён раздел «Тесты» (Testcontainers — для задач с БД), чек-лист ревью |
| 2026-10-23 | architect | DESIGN (без изменений) | T03: NEEDS_REWORK → PLAN_REVIEW — исправлен пример AC-3 (MagicNumber в выражении), явный `failOnSeverity = Error`, фоллбек на новую alpha detekt — только при зелёном `./gradlew build` на нашем стеке, иначе эскалация. Синхронно обновлены ADR-0004 и architecture.md |
| 2026-10-23 | tech-lead | DESIGN → READY | Повторное ревью плана T03: PLAN_REVIEW → READY (замечания круга 1 исправлены). Все задачи T01–T03 READY → фича READY; следующий шаг — kotlin-backend-developer, T01 |
| 2026-10-23 | product-manager | READY → DRAFT | Дополнение пользователя «подключить Kotest для тестов» (до начала разработки): в объём и AC-6 фичи добавлено требование «все тесты на Kotest, выполняются в `./gradlew build`»; T01, T02 → DRAFT (новые AC про Kotest, нужен пересмотр технического плана architect); T03 по продукту не затронута, остаётся READY (architect сверит план со стилем Kotest); Q7 (не блокирует) с допущением; out of scope — моки/property-based/покрытие |
| 2026-10-23 | architect | DRAFT → DESIGN | Kotest: ADR-0005 (Kotest 6.2.6, `FunSpec`, `kotest-runner-junit6` = JUnit 6.0.3 из BOM Boot, `kotest-extensions-spring` через `@ApplyExtension`, только движок `kotest`); дополнены ADR-0002/0003/0004, overview.md, architecture.md. T01, T02: DRAFT → PLAN_REVIEW (планы под Kotest); T03: READY → PLAN_REVIEW (detekt `ForbiddenImport` для JUnit/AssertJ/Hamcrest/`kotlin.test`). Q7 закрыт ответом пользователя (раннер + assertions). Декомпозиция и порядок T01 → T02 → T03 без изменений |
| 2026-10-23 | tech-lead | DESIGN → READY | Ревью плана под Kotest: T01, T02, T03 → READY (только minor: формулировка junit5 vs junit6 в ADR-0005 → architect, доп. негативы AC-9/ForbiddenImport, `shouldNotBeNull()` для `responseBody`). Совместимость Kotest 6.2.6 / kotest-extensions-spring со Spring 7.0.10 / JUnit 6.0.3 / Kotlin 2.3.21 / JDK 17 сверена по байткоду; conventions.md («Тесты», «Сборка и зависимости», чек-лист) обновлён под Kotest FunSpec. Следующий шаг — kotlin-backend-developer, T01 |
| 2026-10-23 | architect | READY (без изменений) | Q8 закрыт решением пользователя: при неработоспособности `kotest-extensions-spring` — переход Spring-тестов на JUnit Jupiter без повторного согласования. Запасной вариант спроектирован в ADR-0005 («Запасной вариант»: триггер с контрольным прогоном, только `@SpringBootTest`-классы на JUnit, Kotest-матчеры везде, `includeEngines("kotest", "junit-jupiter")`, `@TestConstructor(ALL)`, сужение `ForbiddenImport`, перечень правок conventions.md/overview.md); отражён в architecture.md («Риски»), T02 и T03 («Риски»). Закрыт minor tech-lead №1 (T01): обоснование `kotest-runner-junit6` vs `-junit5` уточнено в ADR-0005 и architecture.md. Основной план, AC, декомпозиция и статусы задач не менялись |
| 2026-10-23 | kotlin-backend-developer | READY → IN_PROGRESS | T01 взята в разработку (первая задача фичи) |
| 2026-10-23 | tech-lead | IN_PROGRESS (без изменений) | Ревью запасного варианта ADR-0005: T02 READY (1 minor), T03 NEEDS_REWORK (1 major: шаблоны `ForbiddenImport` запасного варианта пропускают JUnit-проверки Kotlin API → whitelist через `allowedImports`). Основной план не затронут. На T01 (в работе) и T02 не влияет: T03 зависит от них и будет взята позже. Статус фичи не меняется |
| 2026-10-23 | architect | IN_PROGRESS (без изменений) | Круг 4 по запасному варианту ADR-0005: T03 NEEDS_REWORK → PLAN_REVIEW — `ForbiddenImport` переведён на белый список (`forbiddenImports` как в основном плане + `allowedImports: ['org.junit.jupiter.api.Test']`), добавлены негативная проверка `org.junit.jupiter.api.assertThrows` и позитивная проверка; T02 остаётся READY — дополнен перечень правок документов (minor). Синхронно обновлены ADR-0005 и architecture.md. Основной план, AC и декомпозиция не менялись; T01 не затронута |
| 2026-10-23 | tech-lead | IN_PROGRESS (без изменений) | Повторное ревью запасного варианта ADR-0005 (круг 5): T03 PLAN_REVIEW → READY. Major (белый список `allowedImports`) и оба minor закрыты, T03/T02/ADR-0005/architecture.md/Q8 согласованы. Статус фичи не меняется (T01 в работе) |
| 2026-10-23 | qa-engineer | IN_PROGRESS (без изменений) | T01 IN_TESTING → DONE: AC-1..AC-9 подтверждены, дефектов нет; импорт в IntelliJ (AC-7 T01 / AC-4 фичи) — ручная проверка пользователя. Задач DONE: 1/3; следующий шаг — kotlin-backend-developer, T02 |
| 2026-10-23 | tech-lead | IN_PROGRESS (без изменений) | Ревью кода T02: IN_REVIEW → IN_TESTING (0 blocker / 0 major / 3 minor по документации); строка T02 в таблице задач исправлена READY → IN_TESTING. Статус фичи не меняется |
| 2026-10-23 | qa-engineer | IN_PROGRESS (без изменений) | T02 IN_TESTING → DONE: AC-1..AC-8 подтверждены (10 автотестов, 4 добавлены QA; AC-8 — намеренно неверные ожидания роняют build), дефектов нет. Задач DONE: 2/3; следующий шаг — kotlin-backend-developer, T03 |
| 2026-10-24 | qa-engineer | IN_PROGRESS → DONE | T03 IN_TESTING → DONE: AC-1..AC-6 подтверждены (19 ручных кейсов), дефектов нет. Чистый клон: `./gradlew clean build` и `gradlew.bat build` зелёные (ktlint main/test/kts + detekt + 10/10 тестов), приложение без БД отвечает на `/actuator/health`. Все задачи T01–T03 DONE → фича DONE. 2 minor-наблюдения вне AC: обход `ForbiddenImport` через полные имена JUnit (→ architect, ADR-0005), уточнение строки 55 conventions.md (→ tech-lead) |
