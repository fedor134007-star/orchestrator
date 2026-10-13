-- Уникальность email без учёта регистра обеспечивается функциональным индексом:
-- ограничение уровня БД закрывает гонку двух одновременных регистраций.
CREATE UNIQUE INDEX uk_users_email_lower ON person.users (lower(email));

-- Индексы на внешние ключи и поля частого поиска.
CREATE INDEX idx_users_address_id ON person.users (address_id);
CREATE INDEX idx_individuals_user_id ON person.individuals (user_id);
CREATE INDEX idx_addresses_country_id ON person.addresses (country_id);
