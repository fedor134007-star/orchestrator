package com.example.persons_service.service;

import com.example.persons_service.aggregate.AggregatePatch;
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

import java.util.UUID;

/**
 * Сценарии работы с агрегатом пользователя (Spring MVC + Spring Data JPA).
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
    public UserResponse createUser(CreateUserRequest request) {
        CreateUserRequest validated = requestValidator.validateCreate(request);
        ensureEmailFree(validated.getEmail());

        UserAggregate aggregate = assembler.forCreate(validated);
        UserAggregate saved = store.insert(aggregate);
        auditRecorder.recordCreated(saved);

        UserResponse response = mapper.toResponse(saved);
        log.info("Создан пользователь id={}, email={}, filled={}",
                response.getId(), response.getEmail(), response.getFilled());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return mapper.toResponse(store.loadById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        return mapper.toResponse(store.loadByEmail(normalizeEmail(email)));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UpdateUserRequest validated = requestValidator.validateUpdate(request);
        UserAggregate before = store.loadById(id);
        UserAggregate saved = applyUpdate(before, validated);

        UserResponse response = mapper.toResponse(saved);
        log.info("Обновлён пользователь id={}, filled={}", response.getId(), response.getFilled());
        return response;
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        UserAggregate aggregate = store.loadById(id);
        auditRecorder.recordDeleted(aggregate);
        store.delete(aggregate);
        log.info("Удалён пользователь id={}", id);
    }

    // ------------------------------------------------------------------
    // Шаги сценариев
    // ------------------------------------------------------------------

    private void ensureEmailFree(String email) {
        if (store.emailExists(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private UserAggregate applyUpdate(UserAggregate before, UpdateUserRequest request) {
        AggregatePatch patch = assembler.forUpdate(before, request);
        if (!patch.changed()) {
            // Пустой патч не двигает updatedAt и не создаёт ревизию
            log.info("Патч не изменил агрегат id={}", before.user().getId());
            return before;
        }
        ensureEmailStillFree(before, patch.after());
        UserAggregate saved = store.update(patch);
        auditRecorder.recordUpdated(patch, saved);
        return saved;
    }

    private void ensureEmailStillFree(UserAggregate before, UserAggregate after) {
        String email = after.user().getEmail();
        if (email.equalsIgnoreCase(before.user().getEmail())) {
            return;
        }
        if (store.emailExists(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }
}
