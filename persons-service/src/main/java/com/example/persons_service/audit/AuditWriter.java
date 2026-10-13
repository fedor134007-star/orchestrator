package com.example.persons_service.audit;

import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Аудит изменений агрегата.
 *
 * <p>Заменяет Hibernate Envers: Envers — часть Hibernate ORM (блокирующий JPA) и с R2DBC
 * несовместим. Схема и семантика сохранены такими же, как были у Envers, поэтому история
 * читается теми же запросами:</p>
 *
 * <ul>
 *   <li>одна ревизия на одну транзакцию изменения агрегата ({@code revinfo} + {@code revinfo_seq});</li>
 *   <li>{@code revtype}: 0 — создание, 1 — изменение, 2 — удаление;</li>
 *   <li>колонки {@code <свойство>_mod} показывают, какие поля изменились;</li>
 *   <li>при удалении пишется полный снимок состояния (аналог {@code store_data_at_delete}).</li>
 * </ul>
 *
 * <p>Записи аудита выполняются в той же транзакции, что и изменения данных: история не может
 * разойтись с фактическим состоянием агрегата.</p>
 */
@Component
public class AuditWriter {

    public static final short INSERT = 0;
    public static final short UPDATE = 1;
    public static final short DELETE = 2;

    private static final String USER_AUD_SQL = """
            INSERT INTO person.users_aud (rev, revtype, id, secret_key, email, created, updated, first_name, last_name,
                                   filled, address_id, secret_key_mod, email_mod, created_mod, updated_mod,
                                   first_name_mod, last_name_mod, filled_mod, address_mod, individual_mod)
            VALUES (:rev, :revtype, :id, :secret_key, :email, :created, :updated, :first_name, :last_name,
                    :filled, :address_id, :secret_key_mod, :email_mod, :created_mod, :updated_mod,
                    :first_name_mod, :last_name_mod, :filled_mod, :address_mod, :individual_mod)
            """;

    private static final String ADDRESS_AUD_SQL = """
            INSERT INTO person.addresses_aud (rev, revtype, id, created, updated, address, zip_code, archived, city, state)
            VALUES (:rev, :revtype, :id, :created, :updated, :address, :zip_code, :archived, :city, :state)
            """;

    private static final String INDIVIDUAL_AUD_SQL = """
            INSERT INTO person.individuals_aud (rev, revtype, id, user_id, passport_number, phone_number, verified_at,
                                         archived_at, status, passport_number_mod, phone_number_mod, verified_at_mod,
                                         archived_at_mod, status_mod, user_mod)
            VALUES (:rev, :revtype, :id, :user_id, :passport_number, :phone_number, :verified_at,
                    :archived_at, :status, :passport_number_mod, :phone_number_mod, :verified_at_mod,
                    :archived_at_mod, :status_mod, :user_mod)
            """;

    private final DatabaseClient databaseClient;

    public AuditWriter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /** Открывает ревизию и возвращает её номер. */
    public Mono<Integer> openRevision() {
        return databaseClient.sql(
                        "INSERT INTO person.revinfo (rev, revtstmp) VALUES (nextval('person.revinfo_seq'), :timestamp) RETURNING rev")
                .bind("timestamp", System.currentTimeMillis())
                .map((row, metadata) -> row.get("rev", Integer.class))
                .one();
    }

    public Mono<Void> writeUser(int rev,
                                short revType,
                                UserEntity before,
                                UserEntity after,
                                boolean individualChanged) {
        boolean snapshot = revType != UPDATE;

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(USER_AUD_SQL)
                .bind("rev", rev)
                .bind("revtype", revType)
                .bind("id", after.getId());
        spec = bindString(spec, "secret_key", after.getSecretKey());
        spec = bindString(spec, "email", after.getEmail());
        spec = bindTime(spec, "created", after.getCreated());
        spec = bindTime(spec, "updated", after.getUpdated());
        spec = bindString(spec, "first_name", after.getFirstName());
        spec = bindString(spec, "last_name", after.getLastName());
        spec = spec.bind("filled", after.isFilled());
        spec = bindUuid(spec, "address_id", after.getAddressId());
        spec = spec.bind("secret_key_mod", snapshot || !Objects.equals(before.getSecretKey(), after.getSecretKey()));
        spec = spec.bind("email_mod", snapshot || !Objects.equals(before.getEmail(), after.getEmail()));
        spec = spec.bind("created_mod", snapshot || !Objects.equals(before.getCreated(), after.getCreated()));
        spec = spec.bind("updated_mod", snapshot || !Objects.equals(before.getUpdated(), after.getUpdated()));
        spec = spec.bind("first_name_mod", snapshot || !Objects.equals(before.getFirstName(), after.getFirstName()));
        spec = spec.bind("last_name_mod", snapshot || !Objects.equals(before.getLastName(), after.getLastName()));
        spec = spec.bind("filled_mod", snapshot || before.isFilled() != after.isFilled());
        spec = spec.bind("address_mod", snapshot || !Objects.equals(before.getAddressId(), after.getAddressId()));
        spec = spec.bind("individual_mod", snapshot || individualChanged);

        return spec.then();
    }

    public Mono<Void> writeAddress(int rev, short revType, AddressEntity entity) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(ADDRESS_AUD_SQL)
                .bind("rev", rev)
                .bind("revtype", revType)
                .bind("id", entity.getId());
        spec = bindTime(spec, "created", entity.getCreated());
        spec = bindTime(spec, "updated", entity.getUpdated());
        spec = bindString(spec, "address", entity.getAddress());
        spec = bindString(spec, "zip_code", entity.getZipCode());
        spec = bindTime(spec, "archived", entity.getArchived());
        spec = bindString(spec, "city", entity.getCity());
        spec = bindString(spec, "state", entity.getState());
        return spec.then();
    }

    public Mono<Void> writeIndividual(int rev,
                                      short revType,
                                      IndividualEntity before,
                                      IndividualEntity after) {
        boolean snapshot = revType != UPDATE;
        String status = after.getStatus() == null ? null : after.getStatus().name();

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INDIVIDUAL_AUD_SQL)
                .bind("rev", rev)
                .bind("revtype", revType)
                .bind("id", after.getId());
        spec = bindUuid(spec, "user_id", after.getUserId());
        spec = bindString(spec, "passport_number", after.getPassportNumber());
        spec = bindString(spec, "phone_number", after.getPhoneNumber());
        spec = bindTime(spec, "verified_at", after.getVerifiedAt());
        spec = bindTime(spec, "archived_at", after.getArchivedAt());
        spec = bindString(spec, "status", status);
        spec = spec.bind("passport_number_mod",
                snapshot || !Objects.equals(before.getPassportNumber(), after.getPassportNumber()));
        spec = spec.bind("phone_number_mod",
                snapshot || !Objects.equals(before.getPhoneNumber(), after.getPhoneNumber()));
        spec = spec.bind("verified_at_mod",
                snapshot || !Objects.equals(before.getVerifiedAt(), after.getVerifiedAt()));
        spec = spec.bind("archived_at_mod",
                snapshot || !Objects.equals(before.getArchivedAt(), after.getArchivedAt()));
        spec = spec.bind("status_mod", snapshot || before.getStatus() != after.getStatus());
        spec = spec.bind("user_mod", snapshot || !Objects.equals(before.getUserId(), after.getUserId()));

        return spec.then();
    }

    // ------------------------------------------------------------------
    // Привязка параметров: DatabaseClient не принимает null в bind,
    // для nullable-колонок нужен bindNull с явным типом.
    // ------------------------------------------------------------------

    private static DatabaseClient.GenericExecuteSpec bindString(DatabaseClient.GenericExecuteSpec spec,
                                                                String name,
                                                                String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static DatabaseClient.GenericExecuteSpec bindUuid(DatabaseClient.GenericExecuteSpec spec,
                                                              String name,
                                                              UUID value) {
        return value == null ? spec.bindNull(name, UUID.class) : spec.bind(name, value);
    }

    private static DatabaseClient.GenericExecuteSpec bindTime(DatabaseClient.GenericExecuteSpec spec,
                                                              String name,
                                                              OffsetDateTime value) {
        return value == null ? spec.bindNull(name, OffsetDateTime.class) : spec.bind(name, value);
    }
}
