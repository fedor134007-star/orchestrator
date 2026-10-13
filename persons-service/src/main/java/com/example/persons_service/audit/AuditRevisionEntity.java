package com.example.persons_service.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Номер и метка времени ревизии Envers.
 *
 * <p>Таблица {@code person.revinfo} и последовательность {@code person.revinfo_seq} создаются
 * миграцией V005. Шаг последовательности 50 — {@code allocationSize} генератора: Hibernate
 * резервирует диапазон ревизий в памяти, а не обращается к последовательности на каждую
 * ревизию.</p>
 */
@Entity
@RevisionEntity
@Table(name = "revinfo", schema = "person")
public class AuditRevisionEntity {

    @Id
    @RevisionNumber
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq_gen")
    @SequenceGenerator(name = "revinfo_seq_gen", schema = "person", sequenceName = "revinfo_seq", allocationSize = 50)
    @Column(name = "rev", nullable = false)
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long revtstmp;

    public int getRev() {
        return rev;
    }

    public void setRev(int rev) {
        this.rev = rev;
    }

    public long getRevtstmp() {
        return revtstmp;
    }

    public void setRevtstmp(long revtstmp) {
        this.revtstmp = revtstmp;
    }
}
