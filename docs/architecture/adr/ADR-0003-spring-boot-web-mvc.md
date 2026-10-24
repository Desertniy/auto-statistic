# ADR-0003: Веб-стек — Spring Boot 4.1, Web MVC (servlet), Actuator для health-check, ProblemDetail
- Статус: Accepted
- Дата: 2026-10-23
- Фича: F-001

## Контекст
Целевой стек в `overview.md` оставлял открытым выбор Web MVC vs WebFlux + корутины. F-001 создаёт каркас
приложения с проверкой работоспособности по HTTP; на нём будут строиться все фичи. Платформа — JDK 17
(ADR-0001), доступ к PostgreSQL ещё не выбран (будет отдельным ADR), но все рассматриваемые варианты
(Spring Data JDBC, jOOQ, Exposed) — блокирующие JDBC.

## Решение
1. **Spring Boot 4.1.2** (плагин `org.springframework.boot`, BOM — по ADR-0002), Kotlin-плагин `kotlin("plugin.spring")`.
2. **Spring Web MVC (servlet, встроенный Tomcat)**: стартер `spring-boot-starter-webmvc`. Модель — блокирующий
   код, «поток на запрос». Корутины/WebFlux не используются.
3. **Health-check — Spring Boot Actuator**: `GET /actuator/health` → `200 {"status":"UP"}`
   (`503 {"status":"DOWN"}` при отказе индикатора; в Boot 4.1.2 тело может содержать также поле `groups` — имена групп
   проб `liveness`/`readiness`, без деталей; контрактом является поле `status`). По HTTP экспонируется **только** `health`, детали
   (`show-details`) не раскрываются. Собственный контроллер для health не пишется.
4. **Ошибки API — RFC 7807 `ProblemDetail`** включаются с первого дня: `spring.mvc.problemdetails.enabled=true`
   (в том числе 404 на неизвестный путь отдаётся как `application/problem+json`). Доменные ошибки фич будут
   маппиться в `ProblemDetail` согласно `conventions.md`.
5. **JSON — Jackson 3** (по умолчанию в Boot 4) + `tools.jackson.module:jackson-module-kotlin`; `kotlin-reflect` в
   runtime-classpath (требуется Spring для Kotlin).
6. Компилятор Kotlin: `-Xjsr305=strict` (null-аннотации Spring видны как Kotlin-типы),
   `-Xannotation-default-target=param-property` (рекомендация Spring для аннотаций на параметрах конструктора).
7. Конфигурация — `src/main/resources/application.yaml`. Порт по умолчанию 8080, переопределяется без изменения кода
   стандартными механизмами Boot: `SERVER_PORT=…`, `--server.port=…`.
8. Тестирование HTTP — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `RestTestClient`
   (модуль `spring-boot-resttestclient`); тестовые стартеры Boot 4: `spring-boot-starter-webmvc-test`,
   `spring-boot-starter-actuator-test`.
   *Дополнено 2026-10-23 ([ADR-0005](./ADR-0005-kotest-testing.md)):* тесты пишутся как Kotest-спецификации `FunSpec`
   с `@ApplyExtension(SpringExtension::class)` (`io.kotest:kotest-extensions-spring`), бины (`RestTestClient`,
   `ApplicationContext`) — через конструктор спецификации; ответ `RestTestClient` проверяется Kotest-матчерами
   (`exchange().returnResult(…)`), а не цепочками `expectStatus()/expectBody()`.

## Последствия
- Плюсы: самый простой и распространённый стек; естественно сочетается с JDBC и `@Transactional`; отладка и
  стектрейсы проще, чем в реактивном коде; Actuator даёт стандартный health без кода и готов к будущим индикаторам
  (БД добавит свой индикатор автоматически).
- Минусы: на JDK 17 нет виртуальных потоков — масштабирование ограничено пулом Tomcat (для текущих задач достаточно).
  Переход на WebFlux позже дорог — потребует переписать контроллеры и клиентские вызовы.
- Spring Boot 4 переименовал стартеры (`-webmvc` вместо `-web`) и вынес тестовые утилиты в отдельные модули —
  примеры из интернета под Boot 3 могут не работать; ориентироваться на этот ADR и документацию Boot 4.1.

## Рассмотренные альтернативы
- **WebFlux + корутины:** отклонено — БД-доступ планируется блокирующий (JDBC), реактивный стек дал бы сложность
  без выигрыша; R2DBC сужает выбор библиотек доступа к данным.
- **Spring Boot 3.5.x:** отклонено — линейка 4.x актуальна, 3.5 в конце OSS-поддержки; старт новой системы на ней
  означал бы скорую миграцию.
- **Собственный `GET /health`-контроллер:** отклонено — Actuator стандартен, расширяем индикаторами, не требует кода.
- **Spring Boot 4.2.0-RC1:** отклонено — не GA.
