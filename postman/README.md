# Postman: шаблоны запросов

Готовые шаблоны ко всем HTTP-ручкам модуля 2 и оркестратора, со связанными переменными
и проверками ответов.

| Файл | Назначение |
|---|---|
| `person-platform.postman_collection.json` | коллекция: 4 папки, 21 запрос, 54 проверки |
| `local.postman_environment.json` | окружение «Локальный стенд» (адреса и тестовые данные) |

## Как пользоваться

1. Postman → **File → Import** → оба файла.
2. Выбрать окружение **«Локальный стенд»** в правом верхнем углу.
3. Запускать запросы по порядку внутри папки — они связаны переменными:
   * `person-service / 1. Создать пользователя` сохраняет `userId`, `addressId`, `individualId`;
   * `individuals-api / 1. Регистрация` сохраняет `authEmail`, `accessToken`, `refreshToken`, `userUid`.
4. Либо целиком: **Collection Runner → Run** (папки выполняются сверху вниз).

Что внутри:

* **person-service** — 5 операций контракта: создание, чтение по id и email, PATCH, удаление;
* **individuals-api** — регистрация, логин, refresh, `GET /api/v1/auth/me` (Bearer-токен подставляется сам);
* **Проверки ошибок** — сценарии из ТЗ: 409 с `application/problem+json`, 400 на валидации и
  неизвестной стране, 400 на битом UUID, 404 на отсутствующем пользователе и неизвестном пути,
  409 на дубликате регистрации;
* **Стенд** — health и метрики Prometheus обоих сервисов.

## Переменные

`email` и `authEmail` генерируются заново на каждом прогоне, поэтому коллекцию можно
запускать многократно и не получать 409 на создании. Нужен фиксированный адрес —
выставить `autoEmail = false` и задать `email` вручную.

**Важно про окружение:** в `local.postman_environment.json` лежат только статические
значения (адреса, пароль, тестовые имена). Переменные цепочки (`email`, `userId`,
`accessToken`, …) там намеренно отсутствуют: в Postman окружение перекрывает переменные
коллекции, и если продублировать их в окружении, скрипты писали бы значения «в никуда»,
а запросы уходили бы с пустыми подстановками.

## Порты

* person-service: `8082` — алиас из примеров ТЗ; «родной» порт `8092` (в compose опубликованы оба).
* individuals-api: `8091`.

Для другого стенда достаточно поменять `personServiceUrl` / `individualsApiUrl` в окружении.

## Прогон без Postman (CI)

Коллекция проверена прогоном через [newman](https://github.com/postmanlabs/newman) —
22 запроса (один добавляет `pm.sendRequest` в проверке удаления), 54 проверки, 0 падений:

```bash
cd postman
mkdir -p .tools && cd .tools && echo '{"name":"postman-tools","private":true,"version":"1.0.0"}' > package.json
pnpm add newman --store-dir ./.pnpm-store     # или: npm install newman
cd ..
node .tools/node_modules/newman/bin/newman.js run person-platform.postman_collection.json \
     -e local.postman_environment.json --timeout-request 60000
```

Стенд должен быть поднят (`docker compose up -d`), а окружение — указывать на него.

## Известное расхождение в контракте оркестратора

`POST /api/v1/auth/registration` в `individuals-api/openapi/individuals-api.yaml` объявлен
как `201 Created`, а контроллер отдаёт `200 OK`. Проверка в коллекции принимает оба кода
и не сломается, когда расхождение поправят. Правильнее всего привести контроллер к
контракту (`ResponseEntity.status(HttpStatus.CREATED)`) и обновить
`AuthControllerIntegrationTest`, который сейчас ожидает `isOk()`.
