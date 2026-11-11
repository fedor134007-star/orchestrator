-- Мягкое (компенсационное) удаление пользователя.
--
-- Строка остаётся в БД и в аудите Envers, но исключается из чтений:
-- на UserEntity стоит @SQLRestriction("deleted_at IS NULL").
-- Email при этом освобождается — это используется компенсацией individuals-api
-- при неудачной регистрации (частичный уникальный индекс — миграция V010).

ALTER TABLE person.users
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
