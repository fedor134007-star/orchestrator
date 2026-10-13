package com.example.persons_service.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Индивидуальные данные пользователя: паспорт, телефон, статус верификации.
 * Связь с пользователем — через {@code userId} (FK individuals.user_id).
 */
@Table(value = "individuals", schema = "person")
public class IndividualEntity {

    @Id
    private UUID id;

    private UUID userId;

    @Version
    private Long version;

    private String passportNumber;

    private String phoneNumber;

    private OffsetDateTime verifiedAt;

    private OffsetDateTime archivedAt;

    private IndividualStatus status;

    public static IndividualEntity newInstance() {
        IndividualEntity individual = new IndividualEntity();
        individual.setId(UUID.randomUUID());
        individual.setStatus(IndividualStatus.NEW);
        return individual;
    }

    /** Снимок для аудита. */
    public IndividualEntity copy() {
        IndividualEntity copy = new IndividualEntity();
        copy.id = id;
        copy.userId = userId;
        copy.version = version;
        copy.passportNumber = passportNumber;
        copy.phoneNumber = phoneNumber;
        copy.verifiedAt = verifiedAt;
        copy.archivedAt = archivedAt;
        copy.status = status;
        return copy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public IndividualStatus getStatus() {
        return status;
    }

    public void setStatus(IndividualStatus status) {
        this.status = status;
    }
}
