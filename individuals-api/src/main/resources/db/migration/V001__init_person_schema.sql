CREATE SCHEMA IF NOT EXISTS person;
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";



CREATE TABLE person.individuals
(
    id              UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    email           VARCHAR(32),
    password        VARCHAR(32),
    confirmPassword VARCHAR(32),
    firstName       VARCHAR(32),
    lastName        VARCHAR(32),
    created_at      TIMESTAMPTZ NULL,
    archived_at     TIMESTAMPTZ NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'NEW'
);

