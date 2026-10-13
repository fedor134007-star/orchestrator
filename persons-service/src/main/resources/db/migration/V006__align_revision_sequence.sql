-- Ревизии аудита теперь выдаёт сам сервис (реактивный writer вместо Hibernate Envers),
-- поэтому последовательность должна идти с шагом 1: Envers резервировал диапазоны
-- (allocationSize = 50) и увеличивал номера в памяти, а не в базе.
ALTER SEQUENCE person.revinfo_seq INCREMENT BY 1;

-- Подтягиваем последовательность к уже существующим ревизиям: иначе первый же nextval
-- вернул бы номер, который уже занят в person.revinfo.
SELECT setval('person.revinfo_seq',
              GREATEST((SELECT COALESCE(MAX(rev), 0) FROM person.revinfo), 1),
              true);
