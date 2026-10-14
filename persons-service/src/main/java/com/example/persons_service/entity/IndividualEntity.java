package com.example.persons_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Индивидуальные данные пользователя: паспорт, телефон, статус верификации.
 * Связь с пользователем — через {@code userId} (FK individuals.user_id).
 */
@Audited(withModifiedFlag = true)
@Entity
@Table(name = "individuals", schema = "person")
public class IndividualEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "passport_number")
    private String passportNumber;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
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
