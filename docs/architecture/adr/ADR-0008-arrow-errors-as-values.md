# ADR-0008: Ошибки как значения — Arrow `Either`/`Raise`, транзакции и формат ошибок API
- Статус: Accepted
- Дата: 2026-11-08
- Фича: F-002

## Контекст
conventions требуют: ошибки бизнес-логики — значения (`Either` или `sealed …Result`), доменные ошибки — `sealed`,
валидация «parse, don't validate» через smart-конструкторы, композиция без `if`-лестниц. В F-002 появляется первая
доменная логика с накоплением ошибок по полям (регистрация: email, пароль, имя, согласие, версии документов) и
use cases с транзакциями. Нужно: библиотека или свои типы; как не потерять откат транзакции при `Left`; единый
формат ошибок API поверх ProblemDetail (ADR-0003).

Проверено по Maven Central (2026-11-08): `io.arrow-kt:arrow-core` 2.3.0 собран против `kotlin-stdlib` 2.4.21,
2.2.3 — против 2.4.0, **2.2.2.1 — против 2.3.20**. В проекте Kotlin 2.3.21 (ADR-0002), BOM Boot управляет
`kotlin-stdlib` 2.3.21; зависимость на stdlib 2.4.x подняла бы её разрешением конфликтов Gradle выше версии
компилятора. `io.kotest:kotest-assertions-arrow` есть в `kotest-bom` 6.2.6 (зависит от arrow-core 2.1.2 и stdlib 2.2.21).

## Решение
1. **Arrow Core 2.2.2.1** (`io.arrow-kt:arrow-core`, версия `arrow` в каталоге). Используются `Either`, `either { }`,
   `Raise`, `ensure`, `ensureNotNull`, `zipOrAccumulate`/`mapOrAccumulate`, `NonEmptyList`. Optics, fx, resilience —
   не подключаются. Обновление Arrow — только вместе с Kotlin (stdlib Arrow ≤ версии Kotlin проекта).
   В тестах — `io.kotest:kotest-assertions-arrow` (без версии, `kotest-bom`): `shouldBeRight()`, `shouldBeLeft()`.
2. **Где что:**
   - domain: smart-конструкторы `fun parse(raw): Either<XError, X>`, чистые функции `…: Either<DomainError, T>`,
     ошибки — `sealed interface` с исчерпывающим `when` без `else`. Arrow — единственная разрешённая зависимость домена.
   - application: use case возвращает `Either<UseCaseError, Outcome>`; исключения инфраструктуры (БД недоступна)
     не перехватываются и становятся 500 (без деталей наружу — ProblemDetail Spring).
   - api: контроллер маппит `Left` в ProblemDetail исчерпывающим `when`, `Right` — в DTO и статус.
3. **Транзакции и `Left`.** `@Transactional` откатывает только по исключению, а `Left` — обычный возврат. Поэтому
   use cases, которые пишут в БД, используют `com.autostatistic.shared.application.TransactionRunner`:
   ```kotlin
   class TransactionRunner(private val tx: TransactionTemplate) {
       fun <E, A> inTransaction(block: () -> Either<E, A>): Either<E, A>  // Left → status.setRollbackOnly()
   }
   ```
   `@Transactional` на методах, возвращающих `Either`, запрещён (ревью). Побочные эффекты вне БД (письма, запись
   аудита) выполняются **после** возврата из `inTransaction` — use case возвращает из транзакции описание того,
   что нужно отправить, и исполняет это после коммита.
4. **Формат ошибок API** (единый для проекта, поверх RFC 7807 `ProblemDetail`):
   - `type` = `urn:auto-statistic:problem:<code>`, `title` — краткое имя проблемы (рус.), `status`, `detail` —
     понятное пользователю сообщение (рус.), `instance` — путь запроса;
   - расширение `code` — машинный код (= `<code>` из `type`), стабильный контракт для веб-интерфейса;
   - для ошибок валидации (`code = validation-failed`, 400) — расширение `errors`: массив
     `{ "field": "<имя поля DTO>", "code": "<UPPER_SNAKE>", "message": "<рус.>" }`, все ошибки полей сразу
     (накопление через `zipOrAccumulate`);
   - фабрика — `com.autostatistic.shared.api.ApiProblems` (единственное место, где создаётся ProblemDetail проекта);
     стек-трейсы, имена классов и SQL наружу не попадают.
   - DTO запросов — поля nullable (`String?`, `Boolean?`), чтобы отсутствие поля давало ошибку поля в нашем
     формате, а не 400 «Failed to read request» от Jackson. Синтаксически битый JSON — стандартный 400 ProblemDetail Spring.

## Последствия
- (+) Единый идиоматичный инструмент вместо самописных `Result`-типов; накопление ошибок по полям из коробки.
- (+) Явное правило отката транзакций при `Left`; письма и аудит не уходят при откате.
- (−) Новая зависимость в домене; разработчикам нужен Arrow 2 (`Raise`-DSL).
- (−) Версия Arrow привязана к версии Kotlin; на сегодня это не последний релиз (2.3.0).
- (−) `TransactionTemplate` вместо привычного `@Transactional` — правило нужно соблюдать на ревью.

## Рассмотренные альтернативы
- **Свои `sealed …Result`** — без зависимости, но накопление ошибок, `flatMap`/DSL пришлось бы писать самим.
- **`kotlin.Result`** — хранит только `Throwable`, типизированных доменных ошибок нет.
- **Arrow 2.3.0** — последний релиз, но тянет `kotlin-stdlib` 2.4.21 при компиляторе 2.3.21.
- **`@Transactional(rollbackFor)` + исключение-обёртка для `Left`** — смешивает два механизма ошибок.
- **Bean Validation (`spring-boot-starter-validation`)** для DTO — аннотации дают второй, параллельный путь валидации;
  доменные правила (пароль, email) всё равно в smart-конструкторах. Не подключается.
