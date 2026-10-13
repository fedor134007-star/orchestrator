package com.example.persons_service.service;

import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.audit.AuditRecorder;
import com.example.persons_service.exception.EmailAlreadyExistsException;
import com.example.persons_service.mapper.PersonDtoMapper;
import com.example.persons_service.repository.UserAggregateStore;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Сценарии работы с агрегатом пользователя (WebFlux + R2DBC).
 *
 * <p>Класс намеренно тонкий: здесь только порядок шагов и транзакционная граница.
 * Политика агрегата — в {@link UserAggregateAssembler}, обращение к таблицам и порядок
 * записи — в {@link UserAggregateStore}, ревизии — в {@link AuditRecorder},
 * валидация — в {@link RequestValidator}.</p>
 *
 * <p>Транзакция охватывает весь агрегат: изменения фиксируются целиком либо откатываются
 * целиком, а записи аудита идут в той же транзакции, поэтому история не может разойтись
 * с фактическим состоянием.</p>
 */
@Service
public class PersonServiceImpl implements PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonServiceImpl.class);

    private final UserAggregateStore store;
    private final UserAggregateAssembler assembler;
    private final AuditRecorder auditRecorder;
    private final PersonDtoMapper mapper;
    private final RequestValidator requestValidator;

    public PersonServiceImpl(UserAggregateStore store,
                             UserAggregateAssembler assembler,
                             AuditRecorder auditRecorder,
                             PersonDtoMapper mapper,
                             RequestValidator requestValidator) {
        this.store = store;
        this.assembler = assembler;
        this.auditRecorder = auditRecorder;
        this.mapper = mapper;
        this.requestValidator = requestValidator;
    }

    @Override
    @Transactional
    public Mono<UserResponse> createUser(CreateUserRequest request) {
        return requestValidator.validateCreate(request)
                .flatMap(this::ensureEmailFree)
                .flatMap(assembler::forCreate)
                .flatMap(store::insert)
                .flatMap(saved -> auditRecorder.recordCreated(saved).thenReturn(saved))
                .map(mapper::toResponse)
                .doOnNext(response -> log.info("Создан пользователь id={}, email={}, filled={}",
                        response.getId(), response.getEmail(), response.getFilled()));
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<UserResponse> getUserById(UUID id) {
        return store.loadById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<UserResponse> getUserByEmail(String email) {
        return store.loadByEmail(normalizeEmail(email)).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public Mono<UserResponse> updateUser(UUID id, UpdateUserRequest request) {
        return requestValidator.validateUpdate(request)
                .flatMap(valid -> store.loadById(id).flatMap(before -> applyUpdate(before, valid)))
                .map(mapper::toResponse)
                .doOnNext(response -> log.info("Обновлён пользователь id={}, filled={}",
                        response.getId(), response.getFilled()));
    }

    @Override
    @Transactional
    public Mono<Void> deleteUser(UUID id) {
        return store.loadById(id)
                .flatMap(aggregate -> auditRecorder.recordDeleted(aggregate).then(store.delete(aggregate)))
                .doOnSuccess(ignored -> log.info("Удалён пользователь id={}", id));
    }

    // ------------------------------------------------------------------
    // Шаги сценариев
    // ------------------------------------------------------------------

    private Mono<CreateUserRequest> ensureEmailFree(CreateUserRequest request) {
        return store.emailExists(request.getEmail())
                .flatMap(exists -> exists
                        ? Mono.error(new EmailAlreadyExistsException(request.getEmail()))
                        : Mono.just(request));
    }

    private Mono<UserAggregate> applyUpdate(UserAggregate before, UpdateUserRequest request) {
        return assembler.forUpdate(before, request).flatMap(patch -> {
            if (!patch.changed()) {
                // Пустой патч не двигает updatedAt и не создаёт ревизию
                log.info("Патч не изменил агрегат id={}", before.user().getId());
                return Mono.just(before);
            }
            return ensureEmailStillFree(before, patch.after())
                    // defer: запись в БД подписывается только после успешной проверки email
                    .then(Mono.defer(() -> store.update(patch)))
                    .flatMap(saved -> auditRecorder.recordUpdated(patch, saved).thenReturn(saved));
        });
    }

    private Mono<Void> ensureEmailStillFree(UserAggregate before, UserAggregate after) {
        String email = after.user().getEmail();
        if (email.equalsIgnoreCase(before.user().getEmail())) {
            return Mono.empty();
        }
        return store.emailExists(email).flatMap(exists -> exists
                ? Mono.error(new EmailAlreadyExistsException(email))
                : Mono.empty());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }
}
