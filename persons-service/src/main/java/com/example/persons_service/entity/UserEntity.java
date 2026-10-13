package com.example.persons_service.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Учётная запись приложения — корень агрегата пользователя.
 *
 * <p>R2DBC-сущность: без ленивой загрузки и связей-объектов. Ссылка на адрес хранится
 * идентификатором ({@code addressId}), агрегат собирается сервисным слоем явно.</p>
 *
 * <p>Сервис не знает о Keycloak: {@code secretKey} — техническое поле доменной модели.</p>
 */
@Table(value = "users", schema = "person")
public class UserEntity {

    @Id
    private UUID id;

    private String secretKey;

    private String email;

    private OffsetDateTime created;

    private OffsetDateTime updated;

    @Version
    private Long version;

    private String firstName;

    private String lastName;

    private boolean filled;

    private UUID addressId;

    public static UserEntity newInstance() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        return user;
    }

    /** Снимок для аудита: сравнение «до/после» требует независимой копии. */
    public UserEntity copy() {
        UserEntity copy = new UserEntity();
        copy.id = id;
        copy.secretKey = secretKey;
        copy.email = email;
        copy.created = created;
        copy.updated = updated;
        copy.version = version;
        copy.firstName = firstName;
        copy.lastName = lastName;
        copy.filled = filled;
        copy.addressId = addressId;
        return copy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public void setAddressId(UUID addressId) {
        this.addressId = addressId;
    }
}
