-- Аудитные таблицы Hibernate Envers.
--
-- Схема отражает фактическую модель сущностей (плоские FK-колонки address_id/user_id,
-- а не связи-объекты). Имена колонок *_mod генерируются из @Audited(withModifiedFlag = true):
-- флаг для поля addressId называется address_id_mod и т.д.
--
-- Особенности:
--   * поле @Version в аудит не попадает — колонки version в *_aud нет;
--   * @Audited(withModifiedFlag = true) добавляет по колонке <свойство>_mod на каждое
--     аудируемое свойство;
--   * ревизии нумеруются последовательностью с шагом 50 — это allocationSize Hibernate
--     (см. RevisionEntity), последовательность с шагом 1 привела бы к конфликту номеров.

CREATE SEQUENCE person.revinfo_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE person.revinfo
(
    rev      INTEGER NOT NULL,
    revtstmp BIGINT,
    CONSTRAINT revinfo_pkey PRIMARY KEY (rev)
);

CREATE TABLE person.users_aud
(
    rev             INTEGER NOT NULL,
    revtype         SMALLINT,
    id              UUID    NOT NULL,
    secret_key      VARCHAR(64),
    email           VARCHAR(1024),
    created         TIMESTAMP WITH TIME ZONE,
    updated         TIMESTAMP WITH TIME ZONE,
    first_name      VARCHAR(64),
    last_name       VARCHAR(64),
    filled          BOOLEAN,
    address_id      UUID,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    secret_key_mod  BOOLEAN,
    email_mod       BOOLEAN,
    created_mod     BOOLEAN,
    updated_mod     BOOLEAN,
    first_name_mod  BOOLEAN,
    last_name_mod   BOOLEAN,
    filled_mod      BOOLEAN,
    address_id_mod  BOOLEAN,
    deleted_at_mod  BOOLEAN,
    CONSTRAINT users_aud_pkey PRIMARY KEY (rev, id),
    CONSTRAINT fk_users_aud_revinfo FOREIGN KEY (rev) REFERENCES person.revinfo (rev)
);

CREATE TABLE person.addresses_aud
(
    rev            INTEGER NOT NULL,
    revtype        SMALLINT,
    id             UUID    NOT NULL,
    created        TIMESTAMP WITH TIME ZONE,
    updated        TIMESTAMP WITH TIME ZONE,
    country_id     INTEGER,
    address        VARCHAR(128),
    zip_code       VARCHAR(32),
    archived       TIMESTAMP WITH TIME ZONE,
    city           VARCHAR(64),
    state          VARCHAR(64),
    created_mod    BOOLEAN,
    updated_mod    BOOLEAN,
    country_id_mod BOOLEAN,
    address_mod    BOOLEAN,
    zip_code_mod   BOOLEAN,
    archived_mod   BOOLEAN,
    city_mod       BOOLEAN,
    state_mod      BOOLEAN,
    CONSTRAINT addresses_aud_pkey PRIMARY KEY (rev, id),
    CONSTRAINT fk_addresses_aud_revinfo FOREIGN KEY (rev) REFERENCES person.revinfo (rev)
);

CREATE TABLE person.individuals_aud
(
    rev                 INTEGER NOT NULL,
    revtype             SMALLINT,
    id                  UUID    NOT NULL,
    user_id             UUID,
    passport_number     VARCHAR(32),
    phone_number        VARCHAR(32),
    verified_at         TIMESTAMP WITH TIME ZONE,
    archived_at         TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(32),
    user_id_mod         BOOLEAN,
    passport_number_mod BOOLEAN,
    phone_number_mod    BOOLEAN,
    verified_at_mod     BOOLEAN,
    archived_at_mod     BOOLEAN,
    status_mod          BOOLEAN,
    CONSTRAINT individuals_aud_pkey PRIMARY KEY (rev, id),
    CONSTRAINT fk_individuals_aud_revinfo FOREIGN KEY (rev) REFERENCES person.revinfo (rev),
    CONSTRAINT ck_individuals_aud_status CHECK (status IS NULL OR status IN ('NEW', 'VERIFIED', 'ARCHIVED'))
);

-- История читается по идентификатору сущности: индексируем то, по чему её ищут.
CREATE INDEX idx_users_aud_id ON person.users_aud (id);
CREATE INDEX idx_addresses_aud_id ON person.addresses_aud (id);
CREATE INDEX idx_individuals_aud_id ON person.individuals_aud (id);
CREATE INDEX idx_individuals_aud_user_id ON person.individuals_aud (user_id);
