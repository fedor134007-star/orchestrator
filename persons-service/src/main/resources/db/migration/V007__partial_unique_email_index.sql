-- Email уникален только среди активных пользователей: мягко удалённые (deleted_at не null)
-- не занимают слот уникальности. Повторная регистрация с тем же email после компенсационного
-- удаления проходит, а гонка двух одновременных регистраций по-прежнему закрывается индексом.
DROP INDEX IF EXISTS person.uk_users_email_lower;
CREATE UNIQUE INDEX uk_users_email_lower ON person.users (lower(email)) WHERE deleted_at IS NULL;
