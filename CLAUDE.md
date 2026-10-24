# auto-statistic

Kotlin backend-проект (целевой стек: Spring Boot, PostgreSQL — см. `docs/architecture/overview.md`).

## Процесс разработки
Работа ведётся командой субагентов из `.claude/agents/` по регламенту **`docs/WORKFLOW.md`** —
прочитай его перед любой работой над фичами.

- Фичи и задачи: `docs/features/F-NNN-<slug>/` (`feature.md`, `architecture.md`, `TNN-*.md`).
- Архитектура и ADR: `docs/architecture/`. Стандарты кода: `docs/architecture/conventions.md`.
- Когда пользователь описывает новую фичу — запускай `product-manager`, затем веди фичу
  по таблице оркестрации из `docs/WORKFLOW.md` (или пользователь вызывает `/feature`).
- Основная сессия — оркестратор: сама не пишет код фич в обход пайплайна, если пользователь не попросил явно.
