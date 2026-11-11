# transaction-service — диаграммы

PlantUML-исходники архитектуры и сценариев **transaction-service** (модуль 3).
Каждый файл — самостоятельная диаграмма (`@startuml … @enduml`).

| Файл | Что показывает |
|---|---|
| [`01-c4-container.puml`](01-c4-container.puml) | C4-контейнеры: клиент, individuals-api, transaction-service, шарды PostgreSQL, Kafka, наблюдаемость |
| [`02-c4-component.puml`](02-c4-component.puml) | Компоненты внутри transaction-service: HTTP-слой, сервисы, репозитории, Kafka producer/consumer |
| [`03-block-flow.puml`](03-block-flow.puml) | Блок-схема взаимодействия: клиент → individuals-api → transaction-service → Kafka → БД |
| [`04-sequence-deposit.puml`](04-sequence-deposit.puml) | Пополнение: init → confirm → асинхронная финализация |
| [`05-sequence-withdrawal.puml`](05-sequence-withdrawal.puml) | Вывод: init → confirm → успех или возврат средств |
| [`06-sequence-transfer.puml`](06-sequence-transfer.puml) | Перевод: синхронный debit + credit, без Kafka |
| [`07-data-model.puml`](07-data-model.puml) | Модель данных: `wallet_types`, `wallets`, `transactions` |
| [`08-status-lifecycle.puml`](08-status-lifecycle.puml) | Жизненный цикл статусов транзакции |

## Как отрендерить

* **IntelliJ IDEA** — плагин *PlantUML Integration*, открыть файл;
* **VS Code** — расширение *PlantUML*, `Alt+D` (нужен Graphviz/Java);
* **онлайн** — вставить содержимое на <https://www.plantuml.com/plantuml/uml/>.

Диаграммы `01` и `02` используют C4-PlantUML (`!includeurl`), поэтому при рендере
нужен доступ в интернет. Остальные — обычный PlantUML, без внешних зависимостей.

## Ключевые принципы, зафиксированные на диаграммах

1. **`init` не пишет в БД.** Это чистый расчёт: комиссия, доступность, итоговая сумма.
   Записи появляются только на `confirm`.
2. **`confirm` — точка изменения состояния.** Для deposit/withdrawal создаётся
   транзакция `PENDING` и публикуется событие в Kafka; для transfer всё синхронно.
3. **Асинхронная финализация (deposit/withdrawal).** Консьюмер обрабатывает
   `DepositCompleted` / `WithdrawalCompleted` / `WithdrawalFailed` и переводит
   транзакцию в `COMPLETED` или `FAILED` (с возвратом средств при неудаче).
4. **Перевод полностью синхронный.** Debit и credit выполняются в одной транзакции БД,
   Kafka не участвует, итоговый статус — сразу `COMPLETED`.
5. **Кошельки и транзакции — один сервис и одна схема.** `WalletService` на диаграммах —
   внутренний компонент, а не отдельный микросервис.
6. **Шардирование — Apache ShardingSphere JDBC.** Сервис работает с PostgreSQL
   как с одним логическим источником; физически это N шардов.
