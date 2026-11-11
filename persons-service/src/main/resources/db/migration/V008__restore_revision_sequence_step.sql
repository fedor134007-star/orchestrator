-- Envers выдаёт ревизии блоками: последовательность должна идти с шагом 50
-- (allocationSize генератора в AuditRevisionEntity). V006 переводил её на шаг 1 —
-- это было нужно прежнему ручному writer'у, который вызывал nextval на каждую ревизию.
-- Возвращаем шаг 50, не ломая уже выданные номера ревизий.

ALTER SEQUENCE person.revinfo_seq INCREMENT BY 50;

-- Подтягиваем последовательность к максимальной существующей ревизии: иначе первый
-- nextval мог бы вернуть номер, который уже занят в person.revinfo.
SELECT setval('person.revinfo_seq',
              GREATEST((SELECT COALESCE(MAX(rev), 0) FROM person.revinfo), 1),
              true);
