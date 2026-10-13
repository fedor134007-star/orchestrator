-- Мягкое удаление пользователя: строка остаётся в БД и в аудите, но исключается из чтений
-- (UserEntity помечен @SQLRestriction("deleted_at IS NULL")). Email при этом освобождается,
-- поэтому повторная регистрация с тем же email проходит.
-- Используется компенсацией individuals-api при неудачной регистрации.
ALTER TABLE person.users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
