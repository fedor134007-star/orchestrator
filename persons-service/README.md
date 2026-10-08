# person-service (модуль 2, WebFlux + R2DBC)

Микросервис предметной области: хранит и изменяет агрегат пользователя —
учётную запись (`users`), адрес (`addresses`) и индивидуальные данные (`individuals`).
Источник правды для пользовательских данных; `individuals-api` остаётся внешним
оркестратором и единственной точкой интеграции с Keycloak.

**Сервис не обращается к Keycloak** и не хранит его технические сущности.

| Параметр | Значение |
|---|---|
| Стек | Java 25, Spring Boot 4.0.7, Spring Framework 7, **WebFlux** + **Spring Data R2DBC**, PostgreSQL 17 |
| Миграции | Flyway по JDBC на старте приложения (рабочий путь запроса — неблокирующий R2DBC) |
| Аудит | собственный реактивный writer (Hibernate Envers несовместим с R2DBC — см. ниже) |
| Порт | `8092` (в compose дополнительно опубликован алиас `8082`, чтобы команды из ТЗ работали как есть) |
| Схема БД | `person` |
| Артефакты | `person-service.jar` (OCI-образ) и `person-service-client` (библиотека для потребителей) |

## HTTP API

| Операция | Метод и путь | Успех |
|---|---|---|
| Создание пользователя | `POST /api/v1/users` | `201 Created` + `Location` |
| Получение по идентификатору | `GET /api/v1/users/{id}` | `200 OK` |
| Получение по email | `GET /api/v1/users/by-email?email=` | `200 OK` |
| Изменение пользователя | `PATCH /api/v1/users/{id}` | `200 OK` |
| Удаление пользователя | `DELETE /api/v1/users/{id}` | `204 No Content` |

Контракт: [`openapi/person-service.yaml`](openapi/person-service.yaml). Схемы — `CreateUserRequest`,
`UpdateUserRequest`, `UserResponse`, `AddressResponse`, `IndividualResponse`, `ProblemResponse`.

Правила предметной области:

* email уникален без учёта регистра — функциональный уникальный индекс `uk_users_email_lower`
  на уровне БД, а не только проверка в приложении;
* создание, изменение и удаление атомарны на уровне всего агрегата: транзакционная граница
  проходит по сервисному фасаду (`@Transactional` поверх реактивного менеджера транзакций);
* `filled = true`, когда заполнены и адрес, и индивидуальные данные;
* PATCH имеет patch-семантику: отсутствующее поле (в том числе во вложенном объекте)
  не изменяется; попытка создать неполный адрес отклоняется с `400`;
* удаление физическое, история остаётся в аудитных таблицах.

### Ошибки (RFC 9457)

Все ошибки возвращаются как `application/problem+json`:

```json
{
  "type": "https://example.org/problems/email-already-exists",
  "title": "Конфликт данных",
  "status": 409,
  "detail": "Пользователь с таким email уже существует",
  "instance": "/api/v1/users"
}
```

Статусы: `400` — формат/валидация/неизвестная страна/неполный адрес, `404` — пользователь или
ресурс не найден, `409` — email занят, конкурентное изменение (`@Version`), нарушение уникального
индекса, `500` — непредвиденная ошибка. Детализация валидации — в расширении `errors`.

Ошибки обрабатываются в двух местах, потому что в WebFlux это разные пути:

* `@RestControllerAdvice` (`ProblemDetailsAdvice`) — всё, что поднято внутри обработчика;
* `ErrorWebExceptionHandler` (`ProblemDetailsErrorWebExceptionHandler`, `@Order(-2)`) — то, что
  до advice не доходит, например запрос к несуществующему пути: `DispatcherHandler` поднимает
  `ResponseStatusException(404)` вне обработчика. Без этого неизвестный путь отдавал бы
  пустой ответ вместо problem+json.

## Слои сервиса

Сервисный слой разложен по одной ответственности на класс, чтобы сценарии читались как порядок шагов:

| Класс | Ответственность |
|---|---|
| [`PersonServiceImpl`](src/main/java/com/example/persons_service/service/PersonServiceImpl.java) | сценарии (create/read/update/delete) и транзакционная граница; политики не содержит |
| [`UserAggregateAssembler`](src/main/java/com/example/persons_service/service/UserAggregateAssembler.java) | сборка агрегата из запроса, patch-семантика, полнота адреса, пересчёт `filled`, определение того, что реально изменилось |
| [`UserAggregateStore`](src/main/java/com/example/persons_service/repository/UserAggregateStore.java) | чтение и запись агрегата поверх трёх R2DBC-таблиц, порядок операций из-за внешних ключей, «может отсутствовать» для вложенных сущностей |
| [`AuditRecorder`](src/main/java/com/example/persons_service/audit/AuditRecorder.java) | одна ревизия на операцию; сценарии не знают про три аудитные таблицы |
| [`CountryResolver`](src/main/java/com/example/persons_service/service/CountryResolver.java) | справочник стран, согласованность alpha-2/alpha-3, обязательность кода |
| [`RequestValidator`](src/main/java/com/example/persons_service/service/RequestValidator.java) | валидация тела по аннотациям контракта и нормализация email |

Поведенческая деталь: PATCH, который ничего не меняет, не двигает `updatedAt` и не создаёт ревизию —
сравнение «до/после» выполняется до записи, в assembler'е.

## Данные и миграции

Flyway-миграции: [`src/main/resources/db/migration`](src/main/resources/db/migration)

| Версия | Содержимое |
|---|---|
| `V001` | схема `person` и расширение `uuid-ossp` |
| `V002` | таблицы `countries`, `addresses`, `users`, `individuals` |
| `V003` | `uk_users_email_lower`, индексы внешних ключей |
| `V004` | справочник стран (ISO 3166-1) |
| `V005` | аудитные таблицы (`*_aud`, `revinfo`) |
| `V006` | шаг последовательности ревизий = 1 и подтягивание её к существующим ревизиям |

`ddl-auto` отсутствует: схему создают только миграции, R2DBC-сущности её не меняют.

Отличия от рекомендованного в ТЗ фрагмента (осознанные):

* временные метки — `TIMESTAMP WITH TIME ZONE`: для платёжной платформы момент времени должен
  быть однозначным, а контракт отдаёт `date-time` с суффиксом `Z`;
* `users.address_id` объявлен `UNIQUE`: связь «пользователь — адрес» действительно один-к-одному;
* добавлены `CHECK (status IN ('NEW','VERIFIED','ARCHIVED'))` и индексы на `id` в аудитных таблицах.

### Доступ к БД: R2DBC + отдельный JDBC-DataSource для Flyway

* рабочие запросы идут через R2DBC (`spring.r2dbc.*`), пул — `r2dbc-pool`;
* схема указывается явно в маппинге (`@Table(value = "users", schema = "person")`) и в SQL,
  поэтому никакой `search_path` не нужен;
* **Spring Boot намеренно не создаёт JDBC `DataSource`, если в контексте есть реактивный
  `ConnectionFactory`** (защита от смешивания блокирующего и неблокирующего доступа).
  Flyway нужен JDBC, поэтому источник данных объявлен явно —
  [`FlywayDataSourceConfiguration`](src/main/java/com/example/persons_service/config/FlywayDataSourceConfiguration.java);
* вместе с ним Boot поднимает и JDBC-менеджер транзакций, из-за чего `@Transactional` не мог
  выбрать между двумя менеджерами, поэтому автоконфигурация
  `DataSourceTransactionManagerAutoConfiguration` исключена: в контексте остаётся ровно один
  менеджер — реактивный.

### Аудит

Hibernate Envers — часть Hibernate ORM, то есть блокирующего JPA, и с R2DBC работать не может.
Аудит реализован собственным реактивным writer'ом
([`AuditWriter`](src/main/java/com/example/persons_service/audit/AuditWriter.java)),
но **схема и семантика сохранены такими же, как были у Envers**, поэтому история читается
теми же запросами:

* одна ревизия на одну транзакцию изменения агрегата: `revinfo` + `person.revinfo_seq`;
* `revtype`: `0` — создание, `1` — изменение, `2` — удаление;
* колонки `<свойство>_mod` показывают, какие поля изменились
  (`last_name_mod`, `email_mod`, `filled_mod`, `address_mod`, `individual_mod`, …);
* при удалении пишется полный снимок состояния — аналог `store_data_at_delete`;
* записи аудита идут в той же транзакции, что и данные, поэтому история не может разойтись
  с фактическим состоянием.

Смысловое отличие: `users_aud.individual_mod` поднимается, когда в этой ревизии изменились
индивидуальные данные (у Envers флаг относился к обратной связи, колонки для неё в таблице нет).

Проверить историю:

```sql
select rev, revtype, last_name, last_name_mod, email_mod from person.users_aud order by rev;
```

## Контракт-first генерация

Код генерируется **CLI-ядром openapi-generator 7.26.0**, а не Gradle-плагином: плагин заморожен
на 7.14.0 и не умеет `useSpringBoot4`. Результат — в `build/generated/openapi/{server,client}`
(в `src` сгенерированного кода нет).

* сервер: `spring-boot` + `reactive=true` + `interfaceOnly=false` + `delegatePattern=true`
  + `useSpringBoot4=true` → `UsersApiController` (маршрутизация) и `UsersApiDelegate`
  с реактивными сигнатурами `Mono<ResponseEntity<T>>`; тело приходит как `Mono<Dto>`;
* клиент: `spring-http-interface` → `@HttpExchange`-интерфейс `UsersApi` для потребителей.
  **OpenFeign не используется**;
* `annotationLibrary=none` + `documentationProvider=none`: swagger-аннотации не генерируются,
  источник истины — YAML-контракт;
* сгенерированный `UsersApiController` лежит в пакете `net.example.person.api`, поэтому
  сканирование компонентов приложения явно расширено: без этого маршруты не регистрируются.

Валидация тела выполняется **явно в сервисном слое** (`jakarta.validation.Validator`): в WebFlux
параметр имеет тип `Mono<Dto>`, и полагаться на автоматическую проверку тела нельзя — иначе
поведение зависело бы от стека. Перед проверкой email обрезается от пробелов.

## Публикация клиента в Nexus

```bash
cd persons-service && ./gradlew publish
```

* артефакт `net.example:person-service-client` (jar + sources + POM) уходит в `maven-snapshots`
  для `-SNAPSHOT` и в `maven-releases` для релизов — репозиторий выбирается по версии;
* базовый адрес — `NEXUS_BASE_URL` (по умолчанию `http://localhost:8081/repository`),
  учётные данные — `NEXUS_USERNAME` / `NEXUS_PASSWORD` (или `.env`);
* потребители подключаются к групповому адресу `maven-public`;
* POM клиента импортирует `spring-boot-dependencies` и объявляет зависимости без версий.

Сборка образа артефакты не публикует — публикация отделена от сборки образа.

## Наблюдаемость

* `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/actuator/metrics`;
* трейсы — OTLP/HTTP в Alloy (`http://alloy:4318/v1/traces`), дальше Tempo, визуализация в Grafana;
* JSON-логи ([`logback-spring.xml`](src/main/resources/logback-spring.xml)) с `traceId`, `spanId`,
  `service.name`: журнал, метрика и трасса сопоставляются по одному запросу.
  Профиль `plain` переключает вывод на человекочитаемый формат.

Особенность реактивного стека: `userId` не кладётся в MDC — в WebFlux нет потока, привязанного
к запросу, и MDC не переносится между операторами без отдельной контекстной пропагации.
Идентификатор пишется прямо в сообщения логов (`Создан пользователь id=…`, `Обновлён пользователь id=…`).

## Запуск

```bash
docker compose up -d --build persons-service
```

Стенд: `person-service` + `person-service-postgres` + `prometheus` + `grafana` + `tempo` + `loki`
+ `alloy` + `nexus`, при необходимости `individuals-api`.
Схема БД создаётся миграциями при старте: init-скрипты в образ PostgreSQL не монтируются.

Полный сброс учебной БД:

```bash
docker exec person-service-postgres psql -U postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='person' AND pid<>pg_backend_pid()" \
  -c "DROP DATABASE person" -c "CREATE DATABASE person"
docker compose restart persons-service
```

Проверка (порт из примеров ТЗ):

```bash
curl -X POST http://localhost:8082/api/v1/users -H 'Content-Type: application/json' -d '{
  "email":"ivan.petrov@example.org","firstName":"Иван","lastName":"Петров",
  "address":{"countryAlpha3":"RUS","countryAlpha2":"RU","city":"Moscow","state":"Moscow",
             "zipCode":"101000","addressLine":"ул. Пример, д. 1"},
  "individual":{"passportNumber":"1234 567890","phoneNumber":"+79991234567"}}'

curl "http://localhost:8082/api/v1/users/by-email?email=ivan.petrov@example.org"
curl http://localhost:8082/actuator/health
curl http://localhost:8082/actuator/prometheus
```

## Тесты

```bash
cd persons-service && ./gradlew test        # 51 тест
cd persons-service && ./gradlew check       # + проверка покрытия (порог 80% по строкам)
```

* unit: бизнес-правила сервиса (StepVerifier + моки репозиториев и аудита), patch-семантика,
  маппинг DTO, слой ошибок RFC 9457;
* интеграционные на Testcontainers (`postgres:17`) с `WebTestClient`: миграции Flyway, CRUD
  агрегата, каскадное удаление, конкурентное создание одного email (проверяется, что проигравшая
  транзакция не оставляет осиротевший адрес), ревизии аудита с флагами изменённых полей и снимком
  при удалении, `/actuator/health` и `/actuator/prometheus`.

Покрытие строк — 90.0%; сгенерированный код (`net.example.person.api`, `net.example.person.client`,
`net.example.person.dto`) из измерения исключён: он не пишется руками.

## Известные ограничения

* API сервиса не защищён: по ТЗ модуля 2 аутентификация — зона оркестратора/шлюза;
* Keycloak-атрибут `user_uid` оркестратор возвращает клиенту, но пока не записывает в профиль
  пользователя Keycloak — это доработка модуля 1;
* события в Kafka модуль 2 не публикует (следующие модули), но контракт и DTO разделены так,
  чтобы это не потребовало ломающих изменений.
