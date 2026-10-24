# Реестр фич

Доска фич проекта. Ведут агенты по правилам из [`docs/WORKFLOW.md`](../WORKFLOW.md).
Новая фича получает следующий свободный номер.

| ID | Название | Статус | Задачи (done/всего) | Каталог |
|----|----------|--------|---------------------|---------|
| F-001 | Базовый Gradle-проект | DONE | 3/3 | [F-001-project-bootstrap](./F-001-project-bootstrap/feature.md) |
| F-002 | Регистрация и вход с согласием на обработку ПДн | DRAFT | 0/7 | [F-002-registration-login](./F-002-registration-login/feature.md) |
| F-003 | Безопасность аккаунта: сессии, пароли, защита от перебора | DRAFT | 0/5 | [F-003-account-security](./F-003-account-security/feature.md) |
| F-004 | Карточка автомобиля и изоляция данных | DRAFT | 0/3 | [F-004-car-card](./F-004-car-card/feature.md) |
| F-005 | Статусы автомобилей и история машины | DRAFT | 0/5 | [F-005-car-statuses-history](./F-005-car-statuses-history/feature.md) |
| F-006 | Продажа автомобиля и отмена продажи | DRAFT | 0/4 | [F-006-car-sale](./F-006-car-sale/feature.md) |
| F-007 | Архив автомобилей | DRAFT | 0/3 | [F-007-car-archive](./F-007-car-archive/feature.md) |
| F-008 | Категории и учёт затрат по автомобилю | DRAFT | 0/3 | [F-008-expenses](./F-008-expenses/feature.md) |
| F-009 | Финансовая сводка по автомобилю | DRAFT | 0/2 | [F-009-car-summary](./F-009-car-summary/feature.md) |
| F-010 | Аналитика по бизнесу | DRAFT | 0/3 | [F-010-business-analytics](./F-010-business-analytics/feature.md) |
| F-011 | Проверка безопасности и готовность MVP к запуску | DRAFT | 0/4 | [F-011-mvp-security-readiness](./F-011-mvp-security-readiness/feature.md) |
| F-012 | Версионирование приложения | IN_PROGRESS | 0/3 | [F-012-app-versioning](./F-012-app-versioning/feature.md) |

Фичи F-002…F-011 — MVP из [`docs/PRD.md`](../PRD.md). Номер фичи совпадает с порядком реализации: каждая фича
опирается только на предыдущие. Карта и обоснование порядка — PRD, раздел 12.

F-012 — техническая фича вне карты PRD; опирается только на F-001 и может выполняться до F-002
(порядок определяет пользователь).
