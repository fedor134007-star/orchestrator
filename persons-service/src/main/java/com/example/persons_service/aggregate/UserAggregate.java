package com.example.persons_service.aggregate;

import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;

/**
 * Агрегат пользователя, собранный явно: R2DBC не умеет ленивую загрузку связей,
 * поэтому сервисный слой читает таблицы и объединяет их в один объект.
 *
 * @param user       учётная запись (корень агрегата)
 * @param address    адрес или {@code null}, если он ещё не заполнен
 * @param individual индивидуальные данные или {@code null}
 * @param country    страна адреса из справочника ({@code null}, если адреса нет)
 */
public record UserAggregate(UserEntity user,
                            AddressEntity address,
                            IndividualEntity individual,
                            CountryEntity country) {

    /** Доменный инвариант: агрегат заполнен, когда есть и адрес, и индивидуальные данные. */
    public boolean filled() {
        return address != null && individual != null;
    }
}
