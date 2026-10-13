-- Схема person — логическое пространство имён домена пользователей.
-- Расширение uuid-ossp даёт uuid_generate_v4() для первичных ключей доменных сущностей.
CREATE SCHEMA IF NOT EXISTS person;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
