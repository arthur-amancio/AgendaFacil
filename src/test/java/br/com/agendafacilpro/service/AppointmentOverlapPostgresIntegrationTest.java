package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.agendafacilpro.domain.AppointmentStatus;

@SpringBootTest
@Testcontainers
class AppointmentOverlapPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final LocalDateTime NINE = LocalDateTime.of(2035, 1, 10, 9, 0);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void concurrentBookingsForSameProfessionalAndPeriodHaveExactlyOneWinner() throws Exception {
        BookingData data = bookingData();
        CyclicBarrier afterPrecheck = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> booking = () -> concurrentInsert(data, NINE, NINE.plusHours(1), afterPrecheck);
            Future<Boolean> first = executor.submit(booking);
            Future<Boolean> second = executor.submit(booking);

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(blockingCount(data, NINE, NINE.plusHours(1))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adjacentAppointmentsAreAllowed() {
        BookingData data = bookingData();

        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());
        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE.plusHours(1), NINE.plusHours(2), LocalDateTime.now());

        assertThat(blockingCount(data, NINE, NINE.plusHours(2))).isEqualTo(2);
    }

    @Test
    void partialOverlapIsRejected() {
        BookingData data = bookingData();
        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());

        assertOverlapViolation(() -> insertAppointment(
                data,
                AppointmentStatus.CONFIRMED,
                NINE.plusMinutes(30),
                NINE.plusMinutes(90),
                LocalDateTime.now()
        ));
    }

    @Test
    void differentDurationsThatOverlapAreRejected() {
        BookingData data = bookingData();
        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusMinutes(30), LocalDateTime.now());

        assertOverlapViolation(() -> insertAppointment(
                data,
                AppointmentStatus.CONFIRMED,
                NINE.minusMinutes(30),
                NINE.plusMinutes(30),
                LocalDateTime.now()
        ));
    }

    @Test
    void differentProfessionalsCanBeBookedAtTheSameTime() {
        BookingData first = bookingData();
        BookingData second = first.withProfessional(insertProfessional(first.establishmentId()));

        insertAppointment(first, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());
        insertAppointment(second, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());

        assertThat(blockingCount(first.establishmentId(), NINE, NINE.plusHours(1))).isEqualTo(2);
    }

    @Test
    void differentTenantsDoNotInterfere() {
        BookingData first = bookingData();
        BookingData second = bookingData();

        insertAppointment(first, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());
        insertAppointment(second, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());

        assertThat(blockingCount(first, NINE, NINE.plusHours(1))).isEqualTo(1);
        assertThat(blockingCount(second, NINE, NINE.plusHours(1))).isEqualTo(1);
    }

    @Test
    void confirmedAppointmentBlocksTheWholePeriod() {
        BookingData data = bookingData();
        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(2), LocalDateTime.now());

        assertOverlapViolation(() -> insertAppointment(
                data,
                AppointmentStatus.CONFIRMED,
                NINE.plusMinutes(30),
                NINE.plusHours(1),
                LocalDateTime.now()
        ));
    }

    @Test
    void validPendingApprovalBlocksThePeriod() {
        BookingData data = bookingData();
        insertAppointment(data, AppointmentStatus.PENDING_APPROVAL, NINE, NINE.plusHours(1), LocalDateTime.now());

        assertOverlapViolation(() -> insertAppointment(
                data,
                AppointmentStatus.CONFIRMED,
                NINE,
                NINE.plusHours(1),
                LocalDateTime.now()
        ));
    }

    @Test
    void nonBlockingStatusesDoNotPreventANewBooking() {
        for (AppointmentStatus status : List.of(
                AppointmentStatus.CANCELLED,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.NO_SHOW,
                AppointmentStatus.EXPIRED
        )) {
            BookingData data = bookingData();
            insertAppointment(data, status, NINE, NINE.plusHours(1), LocalDateTime.now());
            insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());

            assertThat(blockingCount(data, NINE, NINE.plusHours(1))).isEqualTo(1);
        }
    }

    @Test
    void expiredPendingIsPersistedBeforeTheReplacementBooking() {
        BookingData data = bookingData();
        long pendingId = insertAppointment(
                data,
                AppointmentStatus.PENDING_APPROVAL,
                NINE,
                NINE.plusHours(1),
                LocalDateTime.now().minusHours(2)
        );

        appointmentService.expire(data.establishmentId());

        assertThat(status(pendingId)).isEqualTo(AppointmentStatus.EXPIRED.name());
        insertAppointment(data, AppointmentStatus.CONFIRMED, NINE, NINE.plusHours(1), LocalDateTime.now());
        assertThat(blockingCount(data, NINE, NINE.plusHours(1))).isEqualTo(1);
    }

    @Test
    void migrationRefusesExistingOverlapsWithoutChangingAppointments() {
        String schema = "preflight_" + SEQUENCE.incrementAndGet();
        try {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target(MigrationVersion.fromVersion("7"))
                    .load()
                    .migrate();

            jdbc.update("""
                    INSERT INTO %s.appointments(
                        establishment_id, customer_id, service_item_id, professional_id,
                        start_at, end_at, status, public_token, created_at, updated_at
                    ) VALUES
                        (1, 1, 1, 1, ?, ?, 'CONFIRMED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        (1, 2, 1, 1, ?, ?, 'PENDING_APPROVAL', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(schema), NINE, NINE.plusHours(1), token(), NINE.plusMinutes(30), NINE.plusMinutes(90), token());

            assertThatThrownBy(() -> Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate())
                    .hasMessageContaining("overlapping blocking appointments exist");

            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".appointments", Integer.class);
            assertThat(count).isEqualTo(7);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private boolean concurrentInsert(BookingData data, LocalDateTime start, LocalDateTime end, CyclicBarrier barrier) {
        try {
            Boolean inserted = new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(blockingCount(data, start, end)).isZero();
                await(barrier);
                insertAppointment(data, AppointmentStatus.CONFIRMED, start, end, LocalDateTime.now());
                return true;
            });
            return Boolean.TRUE.equals(inserted);
        } catch (DataAccessException ex) {
            assertSqlState(ex, "23P01");
            return false;
        }
    }

    private BookingData bookingData() {
        int suffix = SEQUENCE.incrementAndGet();
        long requestedEstablishmentId = 10_000L + suffix;
        long establishmentId = jdbc.queryForObject("""
                INSERT INTO establishments(id, name, slug, whatsapp, active)
                VALUES (?, ?, ?, '5517999999999', true)
                RETURNING id
                """, Long.class, requestedEstablishmentId, "Tenant " + suffix, "tenant-" + suffix);
        jdbc.update("""
                INSERT INTO establishment_settings(establishment_id, pending_expiration_minutes)
                VALUES (?, 5)
                """, establishmentId);
        long customerId = jdbc.queryForObject("""
                INSERT INTO customers(establishment_id, name, phone_normalized)
                VALUES (?, 'Cliente Teste', ?)
                RETURNING id
                """, Long.class, establishmentId, "17" + String.format("%09d", suffix));
        long serviceId = jdbc.queryForObject("""
                INSERT INTO service_items(establishment_id, name, duration_minutes, active)
                VALUES (?, 'Servico Teste', 60, true)
                RETURNING id
                """, Long.class, establishmentId);
        long professionalId = insertProfessional(establishmentId);
        return new BookingData(establishmentId, customerId, serviceId, professionalId);
    }

    private long insertProfessional(long establishmentId) {
        return jdbc.queryForObject("""
                INSERT INTO professionals(establishment_id, name, active)
                VALUES (?, 'Profissional Teste', true)
                RETURNING id
                """, Long.class, establishmentId);
    }

    private long insertAppointment(
            BookingData data,
            AppointmentStatus status,
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime createdAt
    ) {
        return jdbc.queryForObject("""
                INSERT INTO appointments(
                    establishment_id, customer_id, service_item_id, professional_id,
                    start_at, end_at, status, public_token, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                data.establishmentId(), data.customerId(), data.serviceId(), data.professionalId(),
                start, end, status.name(), token(), createdAt, createdAt);
    }

    private int blockingCount(BookingData data, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM appointments
                WHERE establishment_id = ?
                  AND professional_id = ?
                  AND status IN ('CONFIRMED', 'PENDING_APPROVAL')
                  AND start_at < ?
                  AND end_at > ?
                """, Integer.class, data.establishmentId(), data.professionalId(), end, start);
    }

    private int blockingCount(long establishmentId, LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM appointments
                WHERE establishment_id = ?
                  AND status IN ('CONFIRMED', 'PENDING_APPROVAL')
                  AND start_at < ?
                  AND end_at > ?
                """, Integer.class, establishmentId, end, start);
    }

    private String status(long appointmentId) {
        return jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, appointmentId);
    }

    private void assertOverlapViolation(Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertSqlState(error, "23P01"));
    }

    private void assertSqlState(Throwable error, String expected) {
        Throwable current = error;
        while (current != null && !(current instanceof SQLException)) {
            current = current.getCause();
        }
        assertThat(current).isInstanceOf(SQLException.class);
        assertThat(((SQLException) current).getSQLState()).isEqualTo(expected);
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Concurrency test barrier failed", ex);
        }
    }

    private String token() {
        return UUID.randomUUID().toString();
    }

    private record BookingData(long establishmentId, long customerId, long serviceId, long professionalId) {

        BookingData withProfessional(long newProfessionalId) {
            return new BookingData(establishmentId, customerId, serviceId, newProfessionalId);
        }
    }
}
