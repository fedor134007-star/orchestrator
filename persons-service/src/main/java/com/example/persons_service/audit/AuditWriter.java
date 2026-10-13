package com.example.persons_service.audit;

import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Аудит изменений агрегата.
 *
 * <p>Схема и семантика совпадают с Hibernate Envers (ревизии, {@code revtype} 0/1/2,
 * флаги {@code *_mod}, полный снимок при удалении), поэтому история читается теми же
 * запросами:</p>
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

    private final NamedParameterJdbcTemplate jdbc;

    public AuditWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Открывает ревизию и возвращает её номер. */
    public int openRevision() {
        return jdbc.getJdbcTemplate().queryForObject(
                "INSERT INTO person.revinfo (rev, revtstmp) VALUES (nextval('person.revinfo_seq'), ?) RETURNING rev",
                Integer.class,
                System.currentTimeMillis());
    }

    public void writeUser(int rev,
                          short revType,
                          UserEntity before,
                          UserEntity after,
                          boolean individualChanged) {
        boolean snapshot = revType != UPDATE;

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rev", rev);
        p.addValue("revtype", revType);
        p.addValue("id", after.getId());
        bind(p, "secret_key", after.getSecretKey(), Types.VARCHAR);
        bind(p, "email", after.getEmail(), Types.VARCHAR);
        bind(p, "created", after.getCreated(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "updated", after.getUpdated(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "first_name", after.getFirstName(), Types.VARCHAR);
        bind(p, "last_name", after.getLastName(), Types.VARCHAR);
        p.addValue("filled", after.isFilled());
        bind(p, "address_id", after.getAddressId(), Types.OTHER);
        p.addValue("secret_key_mod", snapshot || !Objects.equals(before.getSecretKey(), after.getSecretKey()));
        p.addValue("email_mod", snapshot || !Objects.equals(before.getEmail(), after.getEmail()));
        p.addValue("created_mod", snapshot || !Objects.equals(before.getCreated(), after.getCreated()));
        p.addValue("updated_mod", snapshot || !Objects.equals(before.getUpdated(), after.getUpdated()));
        p.addValue("first_name_mod", snapshot || !Objects.equals(before.getFirstName(), after.getFirstName()));
        p.addValue("last_name_mod", snapshot || !Objects.equals(before.getLastName(), after.getLastName()));
        p.addValue("filled_mod", snapshot || before.isFilled() != after.isFilled());
        p.addValue("address_mod", snapshot || !Objects.equals(before.getAddressId(), after.getAddressId()));
        p.addValue("individual_mod", snapshot || individualChanged);

        jdbc.update(USER_AUD_SQL, p);
    }

    public void writeAddress(int rev, short revType, AddressEntity entity) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rev", rev);
        p.addValue("revtype", revType);
        p.addValue("id", entity.getId());
        bind(p, "created", entity.getCreated(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "updated", entity.getUpdated(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "address", entity.getAddress(), Types.VARCHAR);
        bind(p, "zip_code", entity.getZipCode(), Types.VARCHAR);
        bind(p, "archived", entity.getArchived(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "city", entity.getCity(), Types.VARCHAR);
        bind(p, "state", entity.getState(), Types.VARCHAR);

        jdbc.update(ADDRESS_AUD_SQL, p);
    }

    public void writeIndividual(int rev,
                                short revType,
                                IndividualEntity before,
                                IndividualEntity after) {
        boolean snapshot = revType != UPDATE;
        String status = after.getStatus() == null ? null : after.getStatus().name();

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rev", rev);
        p.addValue("revtype", revType);
        p.addValue("id", after.getId());
        bind(p, "user_id", after.getUserId(), Types.OTHER);
        bind(p, "passport_number", after.getPassportNumber(), Types.VARCHAR);
        bind(p, "phone_number", after.getPhoneNumber(), Types.VARCHAR);
        bind(p, "verified_at", after.getVerifiedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "archived_at", after.getArchivedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
        bind(p, "status", status, Types.VARCHAR);
        p.addValue("passport_number_mod",
                snapshot || !Objects.equals(before.getPassportNumber(), after.getPassportNumber()));
        p.addValue("phone_number_mod",
                snapshot || !Objects.equals(before.getPhoneNumber(), after.getPhoneNumber()));
        p.addValue("verified_at_mod",
                snapshot || !Objects.equals(before.getVerifiedAt(), after.getVerifiedAt()));
        p.addValue("archived_at_mod",
                snapshot || !Objects.equals(before.getArchivedAt(), after.getArchivedAt()));
        p.addValue("status_mod", snapshot || before.getStatus() != after.getStatus());
        p.addValue("user_mod", snapshot || !Objects.equals(before.getUserId(), after.getUserId()));

        jdbc.update(INDIVIDUAL_AUD_SQL, p);
    }

    // ------------------------------------------------------------------
    // Привязка параметров: JdbcTemplate не принимает null без явного типа,
    // для nullable-колонок указываем SQL-тип.
    // ------------------------------------------------------------------

    private static void bind(MapSqlParameterSource params, String name, Object value, int sqlType) {
        if (value == null) {
            params.addValue(name, null, sqlType);
        } else {
            params.addValue(name, value);
        }
    }
}
