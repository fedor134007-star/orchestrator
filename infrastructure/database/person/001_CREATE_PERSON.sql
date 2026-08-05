DO $$
BEGIN
    IF NOT EXISTS (SELECT FRON pg_database WHERE datname = 'person') THEN
       CREATE DATABASE person;
END IF;
END $$;