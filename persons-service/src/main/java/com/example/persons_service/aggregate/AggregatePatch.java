package com.example.persons_service.aggregate;

import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.IndividualEntity;

/**
 * Результат применения PATCH к загруженному агрегату.
 *
 * <p>Содержит состояние «до» и «после», а также информацию о том, что именно было затронуто:
 * её используют и запись в БД (порядок и состав операций), и аудит (какие ревизии писать).
 * Решение «что изменилось» принимается один раз — в assembler'е, а не в каждом потребителе.</p>
 *
 * @param before    состояние агрегата до патча
 * @param after     состояние после патча; при {@code changed == false} равен {@code before}
 * @param changed   были ли реальные изменения (сравнение без служебного {@code updated})
 * @param address   что произошло с адресом
 * @param individual что произошло с индивидуальными данными
 */
public record AggregatePatch(UserAggregate before,
                             UserAggregate after,
                             boolean changed,
                             AddressChange address,
                             IndividualChange individual) {

    /** @param created адрес создан этим патчем @param touched поля адреса реально изменились */
    public record AddressChange(AddressEntity entity, boolean created, boolean touched) {
    }

    /** @param created индивидуальные данные созданы этим патчем @param touched поля изменились */
    public record IndividualChange(IndividualEntity entity, boolean created, boolean touched) {
    }
}
