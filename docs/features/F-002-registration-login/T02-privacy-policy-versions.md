# F-002/T02: Версии политики конфиденциальности и согласия

| Поле        | Значение                         |
|-------------|----------------------------------|
| Статус      | READY                            |
| Фича        | [F-002](./feature.md)            |
| Тип         | feature                          |
| Зависит от  | F-002/T01                        |
| Оценка      | S                                |
| Круг правок | 0                                |

<!-- ============ product-manager ============ -->
## Описание
Для регистрации по 152-ФЗ нужны два документа с версиями (PRD FR-PD-3): политика конфиденциальности и согласие
на обработку персональных данных. Юридические тексты пока не готовы, пользователь отложил их (PRD O3). Поэтому
документы — это настраиваемая сущность с текстом-заглушкой, и реальные тексты позже подключаются без изменения кода.

Допущение: новые версии публикует оператор через конфигурацию или загрузку данных при развёртывании. API
и админки для этого в MVP нет.

## Критерии приёмки
- [ ] AC-1: Given приложение без настроенных текстов, When запрошены текущие версии документов без входа, Then
  возвращаются оба документа: тип (политика / согласие), идентификатор версии, дата вступления в силу, текст-заглушка
  и признак «заглушка».
- [ ] AC-2: Given оператор добавил новую версию политики через конфигурацию и перезапустил приложение, When
  запрошены текущие версии, Then политика возвращается в новой версии, а предыдущая версия сохранена и доступна
  по идентификатору.
- [ ] AC-3: Given опубликованная версия, When оператор пытается изменить её текст, сохранив тот же идентификатор,
  Then изменение не применяется и в лог пишется понятная ошибка. Опубликованная версия неизменяема (Допущение),
  потому что на неё ссылаются согласия пользователей.
- [ ] AC-4: Given запрос версии по несуществующему идентификатору, When он отправлен, Then ответ «не найдено».
- [ ] AC-5: Given документы, When запрошены без аутентификации, Then они доступны: это публичная информация (FR-SES-3).

## Открытые вопросы
- (нет) Реальные тексты — PRD O3, отложено пользователем.

<!-- ============ architect ============ -->
## Технический план
Решение — [architecture.md](./architecture.md) (API, «Модель данных» → `legal_document_version`, «Доменная модель» →
`legal.domain`), ошибки и формат ProblemDetail — [ADR-0008](../../architecture/adr/ADR-0008-arrow-errors-as-values.md).
Задача вводит Arrow и первый общий код `shared`.

- **Зависимости:** `io.arrow-kt:arrow-core` (версия `arrow = "2.2.2.1"` в каталоге — не новее, ADR-0008);
  `testImplementation` `io.kotest:kotest-assertions-arrow` (без версии, `kotest-bom`).
- **Модули / пакеты:**
  - `shared.api.ApiProblems` — фабрика ProblemDetail проекта: `fun problem(status: HttpStatus, code: String, title: String, detail: String): ProblemDetail`
    (`type = URI("urn:auto-statistic:problem:$code")`, свойство `code`); в T04 добавится `validation(errors)`.
  - `shared.infrastructure.ClockConfiguration` — `@Bean fun clock(): Clock = Clock.systemUTC()`; константа зоны бизнеса
    `BusinessTime.ZONE = ZoneId.of("Europe/Moscow")` (`shared.domain`, NFR-2).
  - `legal.domain`: `LegalDocumentType`, `LegalDocumentVersionId` (smart-конструктор `parse(raw): Either<InvalidVersionId, LegalDocumentVersionId>`,
    regex `^[a-z0-9][a-z0-9.-]{0,63}$`), `LegalDocument`, `currentOf(...)`, `planSync(...)`, `SyncPlan`, `SyncConflict`,
    `LegalDocumentError.NotFound` — сигнатуры в architecture.md.
  - `legal.application`:
    - `LegalDocumentQueries` — `fun current(): CurrentLegalDocuments` (`privacyPolicy`, `consent`; «сегодня» —
      `LocalDate.now(clock.withZone(BusinessTime.ZONE))`), `fun byId(raw: String): Either<LegalDocumentError, LegalDocument>`
      (неверный формат id ⇒ `NotFound`). Используется T04 для проверки версий.
    - `LegalDocumentSynchronizer.synchronize(configured: List<LegalDocumentDefinition>)` — разбор определений → `planSync` →
      вставка `toInsert` → лог ERROR по каждому конфликту → проверка, что для обоих типов есть текущая версия
      (иначе `IllegalStateException("No current PRIVACY_POLICY version: add app.legal.documents entry with effective-from <= today")`
      — старт падает).
    - Порт `LegalDocumentRepository { findAll(); findById(id); findCurrent(type, today); insertIfAbsent(doc, publishedAt) }`.
  - `legal.infrastructure`:
    - `LegalDocumentsProperties` — `@ConfigurationProperties("app.legal")` `data class(val documents: List<Definition>)`,
      `Definition(id: String, type: LegalDocumentType, effectiveFrom: LocalDate, stub: Boolean, textLocation: Resource)`;
      текст читается из `Resource` в UTF-8.
    - `LegalDocumentStartupSync : SmartInitializingSingleton` — вызывает синхронизацию после создания всех бинов (Flyway
      уже выполнен) и до старта веб-сервера.
    - `JdbcLegalDocumentRepository` — `JdbcClient`; вставка `INSERT … ON CONFLICT DO NOTHING` (два экземпляра,
      стартующие одновременно, не падают; конфликты определяет `planSync` до вставки).
  - `legal.api.LegalDocumentController` — `GET /api/v1/legal-documents/current`, `GET /api/v1/legal-documents/{versionId}`,
    DTO `LegalDocumentResponse(type, versionId, effectiveFrom, text, stub)`, `CurrentLegalDocumentsResponse(documents)`;
    `Left(NotFound)` ⇒ `404 legal-document-not-found` через `ApiProblems`.
- **Конфигурация** (`application.yaml`):
  ```yaml
  app.legal.documents:
    - id: privacy-policy-stub-1
      type: PRIVACY_POLICY
      effective-from: 2026-01-01
      stub: true
      text-location: classpath:legal/privacy-policy-stub-1.md
    - id: personal-data-consent-stub-1
      type: PERSONAL_DATA_CONSENT
      effective-from: 2026-01-01
      stub: true
      text-location: classpath:legal/personal-data-consent-stub-1.md
  ```
  Файлы `src/main/resources/legal/*.md` — текст-заглушка на русском («Текст-заглушка. Юридический текст … будет
  опубликован владельцем сервиса»). Оператор добавляет версию внешним файлом (`--spring.config.additional-location=…`),
  повторяя в списке прежние версии (списки в Boot заменяются целиком; не перечисленные в конфигурации версии остаются в БД —
  синхронизация только добавляет). Это описывается в `README.md` (раздел «Документы политики и согласия»).
- **Модель данных / миграции:** `V002__legal_document_version.sql` — таблица, `UNIQUE (type, effective_from)`, `CHECK`-и
  и триггер `legal_document_version_immutable` (`BEFORE UPDATE OR DELETE`, `RAISE EXCEPTION 'legal_document_version is immutable'`)
  по architecture.md.
- **API-контракт:** см. architecture.md, раздел «API» (`LegalDocument`, `404 legal-document-not-found`).
- **Правила синхронизации (`planSync`):** id есть в БД и все поля (`type`, `effectiveFrom`, `content`, `stub`) совпадают ⇒
  пропуск; id есть, но поле отличается ⇒ `ChangedPublishedVersion(id, fields)` (лог ERROR: `Legal document version '<id>' is
  already published and immutable; configured change of [content] ignored`); новый id с занятой парой `(type, effectiveFrom)`
  ⇒ `DuplicateEffectiveDate`; неверный id/пустой текст ⇒ `InvalidDefinition`. Конфликт не роняет старт.
- **Тесты:**
  - unit `LegalDocumentVersionIdTest`, `PlanSyncTest` (все ветки правил), `CurrentOfTest` (будущая дата не текущая; из двух
    прошедших — более поздняя).
  - `LegalDocumentApiTest` (общий контейнер): AC-1 — оба типа, все поля, `stub = true`; AC-4 — `404` с `code =
    legal-document-not-found` и для несуществующего, и для синтаксически неверного id; AC-5 — без заголовка `Authorization`.
  - `LegalDocumentVersioningTest` (собственный контейнер, `AppRunner`): первый запуск — умолчания; AC-2 — второй запуск с
    `--spring.config.additional-location=classpath:legal-versioning/v2.yaml` (полный список: две заглушки + `privacy-policy-2`
    с `effective-from: 2026-06-01`; списки Boot заменяются целиком, поэтому через `--app.legal.documents[2]…` не задавать) ⇒
    `current` отдаёт `privacy-policy-2`, `privacy-policy-stub-1` доступна по id; AC-3 — третий запуск с
    `legal-versioning/changed.yaml` (у `privacy-policy-stub-1` другой `text-location`) ⇒ в `LogCapture` ERROR с id,
    текст в БД прежний, приложение запущено и отвечает. Управляемое время не нужно — все даты в прошлом.
  - `JdbcLegalDocumentRepositoryTest`: прямой `UPDATE`/`DELETE` строки ⇒ исключение (триггер).
- **Риски:** глобальное состояние документов в общем контейнере — тесты, добавляющие версии, только на собственном контейнере
  (иначе «текущая» версия меняется для других тестов); дата `effective-from` сравнивается с московской датой.
  Опубликованная заглушка неизменяема: реальные тексты подключаются **новыми** версиями с новым id (`stub: false`),
  а не правкой файлов-заглушек.

<!-- ============ tech-lead ============ -->
## Ревью плана
- Вердикт: **READY**
- Декомпозиция и AC: вертикальный срез (миграция → домен → синхронизация → публичный API), AC-1…AC-5 покрыты тестами,
  неизменяемость защищена и в приложении (`planSync`), и в БД (триггер). Согласовано с ADR-0007/0008.
- Замечания (не блокируют, учесть при реализации):
  - [minor] Оценка S занижена: домен + синхронизация при старте + репозиторий + контроллер + миграция + 5 спецификаций — это M.
    → Поправить оценку на M (на план не влияет).
  - [minor] `planSync`: не описаны конфликты внутри самой конфигурации — два определения с одним `id` и два новых определения
    с одной парой `(type, effectiveFrom)`. → Считать их `InvalidDefinition`/`DuplicateEffectiveDate` и покрыть в `PlanSyncTest`.
  - [minor] Не указано поведение, если `text-location` не читается (файла нет). → Падать при старте с понятным сообщением
    (ошибка конфигурации оператора, как при отсутствии текущей версии) и зафиксировать это в README; если выбрано иначе —
    описать в «Отклонениях».
  - [minor] `LegalDocumentQueries.current()` возвращает не-null, а `findCurrent` — nullable: без `!!`, через
    `checkNotNull(...) { "No current … version" }` (инвариант гарантирован синхронизацией при старте).
  - [minor] `LegalDocumentApiTest` (AC-4) — проверить и `instance` (= путь запроса) в ProblemDetail: это часть формата ADR-0008,
    и T02 — первая задача, которая его вводит.

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
| 2026-10-24 | product-manager | — → DRAFT | Задача создана при декомпозиции MVP (PRD, раздел 12) |
| 2026-11-08 | architect | DRAFT → PLAN_REVIEW | Технический план: legal_document_version (V002, неизменяемость триггером), синхронизация из app.legal.documents при старте (insert-only), публичный API, Arrow (ADR-0008) |
| 2026-11-08 | tech-lead | PLAN_REVIEW → READY | Ревью плана: READY, 5 minor (оценка → M, конфликты внутри конфигурации, нечитаемый text-location, instance) |
