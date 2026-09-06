CREATE SCHEMA IF NOT EXISTS person;
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";



CREATE TABLE persons
(
    user_uid     UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    email        VARCHAR(255)             NOT NULL UNIQUE,
    first_name   VARCHAR(100)             NOT NULL,
    last_name    VARCHAR(100)             NOT NULL,
    middle_name  VARCHAR(100),
    phone_number VARCHAR(20),
    status       VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
