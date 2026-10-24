# ADR-0002: Сборка — Gradle Kotlin DSL, Wrapper, version catalog, один модуль, BOM Spring Boot через platform
- Статус: Accepted
- Дата: 2026-10-23
- Фича: F-001

## Контекст
Репозиторий — IntelliJ-модуль без системы сборки. Пайплайн разработки (`docs/WORKFLOW.md`) опирается на
`./gradlew build`. Нужна воспроизводимая сборка на Windows и Unix без предустановленного Gradle, с
зафиксированными версиями, совместимая с JDK 17 (ADR-0001), Spring Boot 4.1 (ADR-0003) и инструментами
качества (ADR-0004).

Совместимость версий (проверено по официальной документации на 2026-10-23):
- Spring Boot 4.1.2 (текущий stable): Gradle 8.14+ / 9.x, Kotlin ≥ 2.2, управляет Kotlin **2.3.21**, JUnit Jupiter 6.0.3.
- Kotlin Gradle Plugin 2.3.20–2.3.21: Gradle 7.6.3–9.3.x.
- detekt 2.0.0-alpha.3 собран и протестирован с Gradle **9.3.1** / Kotlin **2.3.21**.

## Решение
1. **Gradle Kotlin DSL + Gradle Wrapper 9.3.1** (`distributionType=bin`, в `gradle-wrapper.properties` указан
   `distributionSha256Sum`). Файлы wrapper коммитятся; `gradlew` — с битом исполнения в git, `.gitattributes`
   фиксирует окончания строк (`gradlew` — LF, `*.bat` — CRLF).
2. **Kotlin 2.3.21** — ровно та версия, которой управляет Spring Boot 4.1.2 (нет расхождения между KGP и BOM).
3. **Version catalog** `gradle/libs.versions.toml` — единственное место с версиями плагинов и библиотек.
   В `build.gradle.kts` версии литералами не пишутся.
4. **Один Gradle-модуль** (корневой проект `auto-statistic`). Слои `api / application / domain / infrastructure`
   разделяются **пакетами**, а не модулями (см. раздел «Структура пакетов» в `overview.md`). Многомодульность —
   отдельным ADR, когда появится реальная причина (например, переиспользуемый domain или контроль слоёв
   компилятором).
5. **Spring Boot BOM подключается через Gradle platform**:
   `implementation(platform(SpringBootPlugin.BOM_COORDINATES))`; плагин `io.spring.dependency-management`
   **не используется**. Версии библиотек без явной версии в каталоге берутся из BOM.
6. **Координаты:** `group = "com.autostatistic"`, `version = "0.0.1-SNAPSHOT"`, базовый пакет `com.autostatistic`,
   `rootProject.name = "auto-statistic"`.
7. Репозитории объявляются один раз в `settings.gradle.kts` (`dependencyResolutionManagement { repositories { mavenCentral() } }`),
   плагины — из Gradle Plugin Portal (по умолчанию).
8. Тесты — JUnit Platform (`useJUnitPlatform()`); `org.junit.platform:junit-platform-launcher` объявляется явно
   в `testRuntimeOnly` (Gradle 9 не добавляет его неявно).
   *Дополнено 2026-10-23 ([ADR-0005](./ADR-0005-kotest-testing.md)):* тестовый движок — Kotest (`kotest-runner-junit6`),
   `useJUnitPlatform { includeEngines("kotest") }`; BOM Kotest подключается тем же способом — `platform(libs.kotest.bom)`.

## Последствия
- Плюсы: сборка воспроизводима (wrapper + checksum + catalog); одна точка обновления версий; platform-BOM не
  «переопределяет» версии в чужих конфигурациях (важно для detekt/ktlint, у которых свой Kotlin-компилятор —
  с dependency-management plugin это частая причина ошибок «compiled with Kotlin X but running with Y»).
- Минусы: platform-подход чуть многословнее (BOM нужно добавить в каждую используемую конфигурацию —
  `implementation`, при необходимости `developmentOnly`/`testFixtures`); нельзя переопределять версии свойствами
  вида `extra["kotlin.version"]` — только через каталог/constraints.
- Слои внутри одного модуля не защищены компилятором — нарушения ловятся ревью (позже — ArchUnit/Konsist, если понадобится).
- Обновление Kotlin/Gradle выполняется согласованно с ADR-0004 (detekt жёстко привязан к версиям Kotlin/Gradle).

## Рассмотренные альтернативы
- **Maven:** отклонено — целевой стек и шаблоны Spring/Kotlin ориентированы на Gradle Kotlin DSL; catalog удобнее.
- **Gradle 9.8.x (новейший) + Kotlin 2.4.x:** отклонено — Kotlin 2.4 не управляется Spring Boot 4.1 (пришлось бы
  переопределять версию Kotlin в обход BOM); KGP 2.3.x официально поддерживает Gradle только до 9.3.
- **Плагин `io.spring.dependency-management`:** отклонено — см. «Последствия» (вмешательство в конфигурации detekt).
- **Многомодульная сборка по слоям сразу:** отклонено (YAGNI) — избыточно для пустого проекта.
- **buildSrc / convention plugins:** отклонено (YAGNI) при одном модуле.
