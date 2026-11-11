package com.example.persons_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Учётная запись приложения — корень агрегата пользователя.
 *
 * <p>JPA-сущность без ленивой загрузки связей-объектов: ссылка на адрес хранится
 * идентификатором ({@code addressId}), агрегат собирается сервисным слоем явно —
 * это сохраняет явный контроль над порядком вставки и аудитом.</p>
 *
 * <p>Мягкое удаление — флаг {@code deletedAt}: строка остаётся в БД (и в аудите Envers),
 * но все чтения её не видят ({@code @SQLRestriction}). Email при этом освобождается —
 * это и есть компенсационное удаление при неудачной регистрации.</p>
 *
 * <p>Сервис не знает о Keycloak: {@code secretKey} — техническое поле доменной модели.</p>
 */
@Audited(withModifiedFlag = true)
@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "users", schema = "person")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "secret_key")
    private String secretKey;

    @Column(name = "email")
    private String email;

    @Column(name = "created")
    private OffsetDateTime created;

    @Column(name = "updated")
    private OffsetDateTime updated;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "filled", nullable = false)
    private boolean filled;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

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
        copy.deletedAt = deletedAt;
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
