-- Агрегат пользователя: countries (справочник) → addresses → users → individuals.
--
-- Даты жизненного цикла (verified_at, archived_at, archived) допускают NULL:
-- у новой записи они естественно отсутствуют.
--
-- Отличие от рекомендованного фрагмента ТЗ: временные метки объявлены как
-- TIMESTAMP WITH TIME ZONE. Для платёжной платформы момент времени должен быть
-- однозначным (устойчивым к DST и смене таймзоны сервера), а контракт отдаёт
-- даты в формате date-time с суффиксом Z. Это позволяет отображать их в
-- OffsetDateTime без потери смысла.

CREATE TABLE person.countries
(
    id      SERIAL PRIMARY KEY,
    created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    name    VARCHAR(32)              NOT NULL,
    alpha2  VARCHAR(2)               NOT NULL,
    alpha3  VARCHAR(3)               NOT NULL,
    status  VARCHAR(32)              NOT NULL,
    CONSTRAINT uk_countries_alpha2 UNIQUE (alpha2),
    CONSTRAINT uk_countries_alpha3 UNIQUE (alpha3)
);

CREATE TABLE person.addresses
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version    BIGINT                   NOT NULL DEFAULT 0,
    country_id INTEGER REFERENCES person.countries (id),
    address    VARCHAR(128)             NOT NULL,
    zip_code   VARCHAR(32),
    archived   TIMESTAMP WITH TIME ZONE          DEFAULT NULL,
    city       VARCHAR(64)              NOT NULL,
    state      VARCHAR(64)
);

CREATE TABLE person.users
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    secret_key VARCHAR(64),
    email      VARCHAR(1024)            NOT NULL,
    created    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version    BIGINT                   NOT NULL DEFAULT 0,
    first_name VARCHAR(64)              NOT NULL,
    last_name  VARCHAR(64)              NOT NULL,
    filled     BOOLEAN                  NOT NULL DEFAULT FALSE,
    address_id UUID UNIQUE REFERENCES person.addresses (id)
);

CREATE TABLE person.individuals
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID UNIQUE REFERENCES person.users (id),
    version         BIGINT                   NOT NULL DEFAULT 0,
    passport_number VARCHAR(32),
    phone_number    VARCHAR(32),
    verified_at     TIMESTAMP WITH TIME ZONE          DEFAULT NULL,
    archived_at     TIMESTAMP WITH TIME ZONE          DEFAULT NULL,
    status          VARCHAR(32)              NOT NULL,
    CONSTRAINT ck_individuals_status CHECK (status IN ('NEW', 'VERIFIED', 'ARCHIVED'))
);
