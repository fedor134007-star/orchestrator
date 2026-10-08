package com.example.persons_service.audit;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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

    public Mono<Void> recordCreated(UserAggregate aggregate) {
        UserEntity user = aggregate.user();
        AddressEntity address = aggregate.address();
        IndividualEntity individual = aggregate.individual();

        return auditWriter.openRevision()
                .flatMap(rev -> auditWriter.writeUser(rev, AuditWriter.INSERT, user, user, individual != null)
                        .then(address == null
                                ? Mono.empty()
                                : auditWriter.writeAddress(rev, AuditWriter.INSERT, address))
                        .then(individual == null
                                ? Mono.empty()
                                : auditWriter.writeIndividual(rev, AuditWriter.INSERT, individual, individual)));
    }

    public Mono<Void> recordUpdated(AggregatePatch patch, UserAggregate saved) {
        AggregatePatch.IndividualChange individual = patch.individual();
        boolean individualChanged = individual != null && individual.touched();

        return auditWriter.openRevision().flatMap(rev -> {
            Mono<Void> userAudit = auditWriter.writeUser(
                    rev, AuditWriter.UPDATE, patch.before().user(), saved.user(), individualChanged);

            Mono<Void> addressAudit = patch.address() != null && patch.address().touched()
                    ? auditWriter.writeAddress(
                            rev,
                            patch.address().created() ? AuditWriter.INSERT : AuditWriter.UPDATE,
                            saved.address())
                    : Mono.empty();

            Mono<Void> individualAudit = individualChanged
                    ? auditWriter.writeIndividual(
                            rev,
                            individual.created() ? AuditWriter.INSERT : AuditWriter.UPDATE,
                            individual.created() ? saved.individual() : patch.before().individual(),
                            saved.individual())
                    : Mono.empty();

            return userAudit.then(addressAudit).then(individualAudit);
        });
    }

    public Mono<Void> recordDeleted(UserAggregate aggregate) {
        UserEntity user = aggregate.user();
        AddressEntity address = aggregate.address();
        IndividualEntity individual = aggregate.individual();

        // Снимок состояния на момент удаления (аналог store_data_at_delete в Envers)
        return auditWriter.openRevision()
                .flatMap(rev -> auditWriter.writeUser(rev, AuditWriter.DELETE, user, user, individual != null)
                        .then(address == null
                                ? Mono.empty()
                                : auditWriter.writeAddress(rev, AuditWriter.DELETE, address))
                        .then(individual == null
                                ? Mono.empty()
                                : auditWriter.writeIndividual(rev, AuditWriter.DELETE, individual, individual)));
    }
}
