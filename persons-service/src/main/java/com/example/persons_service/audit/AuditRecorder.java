package com.example.persons_service.audit;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import org.springframework.stereotype.Component;

/**
 * Ревизии аудита по сценариям агрегата.
 *
 * <p>Сценарии не должны знать, что ревизия открывается отдельным запросом и что в аудите
 * три таблицы: один вызов на операцию — и всё, что нужно, попадает в одну ревизию.
 * Низкоуровневая запись колонок остаётся в {@link AuditWriter}.</p>
 */
@Component
public class AuditRecorder {

    private final AuditWriter auditWriter;

    public AuditRecorder(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    public void recordCreated(UserAggregate aggregate) {
        UserEntity user = aggregate.user();
        AddressEntity address = aggregate.address();
        IndividualEntity individual = aggregate.individual();

        int rev = auditWriter.openRevision();
        auditWriter.writeUser(rev, AuditWriter.INSERT, user, user, individual != null);
        if (address != null) {
            auditWriter.writeAddress(rev, AuditWriter.INSERT, address);
        }
        if (individual != null) {
            auditWriter.writeIndividual(rev, AuditWriter.INSERT, individual, individual);
        }
    }

    public void recordUpdated(AggregatePatch patch, UserAggregate saved) {
        AggregatePatch.IndividualChange individual = patch.individual();
        boolean individualChanged = individual != null && individual.touched();

        int rev = auditWriter.openRevision();
        auditWriter.writeUser(rev, AuditWriter.UPDATE, patch.before().user(), saved.user(), individualChanged);

        if (patch.address() != null && patch.address().touched()) {
            auditWriter.writeAddress(
                    rev,
                    patch.address().created() ? AuditWriter.INSERT : AuditWriter.UPDATE,
                    saved.address());
        }
        if (individualChanged) {
            auditWriter.writeIndividual(
                    rev,
                    individual.created() ? AuditWriter.INSERT : AuditWriter.UPDATE,
                    individual.created() ? saved.individual() : patch.before().individual(),
                    saved.individual());
        }
    }

    public void recordDeleted(UserAggregate aggregate) {
        UserEntity user = aggregate.user();
        AddressEntity address = aggregate.address();
        IndividualEntity individual = aggregate.individual();

        // Снимок состояния на момент удаления (аналог store_data_at_delete в Envers)
        int rev = auditWriter.openRevision();
        auditWriter.writeUser(rev, AuditWriter.DELETE, user, user, individual != null);
        if (address != null) {
            auditWriter.writeAddress(rev, AuditWriter.DELETE, address);
        }
        if (individual != null) {
            auditWriter.writeIndividual(rev, AuditWriter.DELETE, individual, individual);
        }
    }
}
