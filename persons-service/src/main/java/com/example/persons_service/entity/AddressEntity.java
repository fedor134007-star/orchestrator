package com.example.persons_service.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Адрес пользователя. Ссылка на справочник стран хранится идентификатором
 * ({@code countryId}), аудируется вместе с агрегатом.
 */
@Table(value = "addresses", schema = "person")
public class AddressEntity {

    @Id
    private UUID id;

    private OffsetDateTime created;

    private OffsetDateTime updated;

    @Version
    private Long version;

    private Integer countryId;

    private String address;

    private String zipCode;

    private OffsetDateTime archived;

    private String city;

    private String state;

    public static AddressEntity newInstance() {
        AddressEntity address = new AddressEntity();
        address.setId(UUID.randomUUID());
        return address;
    }

    /** Снимок для аудита. */
    public AddressEntity copy() {
        AddressEntity copy = new AddressEntity();
        copy.id = id;
        copy.created = created;
        copy.updated = updated;
        copy.version = version;
        copy.countryId = countryId;
        copy.address = address;
        copy.zipCode = zipCode;
        copy.archived = archived;
        copy.city = city;
        copy.state = state;
        return copy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getCreated() {
        return created;
    }

    public void setCreated(OffsetDateTime created) {
        this.created = created;
    }

    public OffsetDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated) {
        this.updated = updated;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Integer getCountryId() {
        return countryId;
    }

    public void setCountryId(Integer countryId) {
        this.countryId = countryId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public OffsetDateTime getArchived() {
        return archived;
    }

    public void setArchived(OffsetDateTime archived) {
        this.archived = archived;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
