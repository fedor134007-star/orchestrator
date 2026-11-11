-- Приводим аудитные таблицы Envers к фактической модели сущностей.
--
-- Ранее (V005) сущности описывались связями-объектами, поэтому Envers генерировал
-- флаги address_mod / individual_mod / user_mod. Сейчас связи хранятся плоскими
-- FK-полями (address_id, user_id), поэтому флаги называются address_id_mod / user_id_mod,
-- а флаг individual_mod не нужен вовсе. Также появились новые аудируемые поля
-- (country_id у адреса, deleted_at у пользователя).
--
-- Миграция идемпотентна по колонкам: на базе, обновлённой из main (V001–V006),
-- колонки переименовываются; на чистой базе — создаются новые версии.

DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = 'person'
                 AND table_name = 'users_aud'
                 AND column_name = 'address_mod') THEN
        ALTER TABLE person.users_aud RENAME COLUMN address_mod TO address_id_mod;
    END IF;
END $$;

ALTER TABLE person.users_aud
    ADD COLUMN IF NOT EXISTS address_id_mod BOOLEAN;

ALTER TABLE person.users_aud
    DROP COLUMN IF EXISTS individual_mod;

ALTER TABLE person.users_aud
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE person.users_aud
    ADD COLUMN IF NOT EXISTS deleted_at_mod BOOLEAN;

-- addresses_aud: в исторической V005 флагов *_mod не было вовсе, а сущность адреса
-- аудируется с withModifiedFlag = true — добавляем флаг на каждое аудируемое поле.
ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS country_id INTEGER;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS created_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS updated_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS country_id_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS address_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS zip_code_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS archived_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS city_mod BOOLEAN;

ALTER TABLE person.addresses_aud
    ADD COLUMN IF NOT EXISTS state_mod BOOLEAN;

DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = 'person'
                 AND table_name = 'individuals_aud'
                 AND column_name = 'user_mod') THEN
        ALTER TABLE person.individuals_aud RENAME COLUMN user_mod TO user_id_mod;
    END IF;
END $$;

ALTER TABLE person.individuals_aud
    ADD COLUMN IF NOT EXISTS user_id_mod BOOLEAN;
