package com.pulseclinic;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulseclinic.common.web.HealthResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PulseClinicApplicationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulseclinic")
            .withUsername("pulseclinic")
            .withPassword("pulseclinic");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("DATABASE_USERNAME", POSTGRES::getUsername);
        registry.add("DATABASE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthIsUpAfterMigrations() {
        ResponseEntity<HealthResponse> response =
                restTemplate.getForEntity("/api/v1/health", HealthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
    }

    @Test
    void flywayAppliedV1Schema() {
        Integer settings = jdbcTemplate.queryForObject(
                "select count(*) from clinic_settings", Integer.class);
        assertThat(settings).isEqualTo(1);

        Integer appointments = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'appointments'",
                Integer.class);
        assertThat(appointments).isEqualTo(1);
    }

    @Test
    void onlyOneOfTwoConcurrentBookingsCanClaimTheSameSlot() throws Exception {
        BookingFixture fixture = seedBookingFixture();
        String accessToken = login(fixture.patientEmail());
        String csrfToken = csrfToken();
        Map<String, Object> request = Map.of(
                "patientId", fixture.patientId().toString(),
                "doctorId", fixture.doctorId().toString(),
                "serviceId", fixture.serviceId().toString(),
                "startsAt", fixture.startsAt().toString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<String>> book = () -> restTemplate.postForEntity(
                    "/api/v1/appointments", authenticatedRequest(request, accessToken, csrfToken), String.class);
            List<Future<ResponseEntity<String>>> results = executor.invokeAll(List.of(book, book));
            List<HttpStatusCode> statuses = results.stream().map(result -> {
                try {
                    return result.get().getStatusCode();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).toList();

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(jdbcTemplate.queryForObject("select count(*) from appointments", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void waitlistOfferIsAcceptedOnlyThroughTheNormalBookingPath() {
        BookingFixture fixture = seedBookingFixture();
        UUID staffUserId = UUID.randomUUID();
        String staffEmail = "staff-" + staffUserId + "@example.test";
        jdbcTemplate.update("insert into users (id, email, password_hash, role) values (?, ?, ?, 'STAFF')",
                staffUserId, staffEmail,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("A-safe-test-password-1"));

        String patientToken = login(fixture.patientEmail());
        String csrfToken = csrfToken();
        ResponseEntity<String> joined = restTemplate.exchange(
                "/api/v1/waitlist",
                org.springframework.http.HttpMethod.POST,
                authenticatedRequest(Map.of(
                        "serviceId", fixture.serviceId().toString(),
                        "doctorId", fixture.doctorId().toString(),
                        "requestedDate", fixture.startsAt().atOffset(ZoneOffset.UTC).toLocalDate().toString()),
                        patientToken, csrfToken),
                String.class);
        assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID waitlistId = jdbcTemplate.queryForObject("select id from waitlist_entries order by created_at desc limit 1", UUID.class);

        String staffToken = login(staffEmail);
        ResponseEntity<String> offered = restTemplate.exchange(
                "/api/v1/waitlist/" + waitlistId + "/offer",
                org.springframework.http.HttpMethod.POST,
                authenticatedRequest(Map.of("startsAt", fixture.startsAt().toString()), staffToken, csrfToken),
                String.class);
        assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("select status from waitlist_entries where id=?", String.class, waitlistId))
                .isEqualTo("OFFERED");

        ResponseEntity<String> accepted = restTemplate.exchange(
                "/api/v1/waitlist/" + waitlistId + "/accept",
                org.springframework.http.HttpMethod.POST,
                authenticatedRequest(Map.of(), patientToken, csrfToken),
                String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("select count(*) from appointments", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from waitlist_entries where id=?", String.class, waitlistId))
                .isEqualTo("FULFILLED");
    }

    private BookingFixture seedBookingFixture() {
        UUID doctorUserId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID specialtyId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        String patientEmail = "patient-" + patientUserId + "@example.test";
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        Instant startsAt = date.atTime(9, 0).toInstant(ZoneOffset.UTC);

        jdbcTemplate.update("insert into users (id, email, password_hash, role) values (?, ?, 'test', 'DOCTOR')",
                doctorUserId, "doctor-" + doctorUserId + "@example.test");
        jdbcTemplate.update("insert into specialties (id, name) values (?, ?)", specialtyId, "General " + specialtyId);
        jdbcTemplate.update(
                "insert into doctors (id, user_id, specialty_id, full_name) values (?, ?, ?, 'Dr Test')",
                doctorId, doctorUserId, specialtyId);
        jdbcTemplate.update(
                "insert into services (id, name, duration_minutes, price_cents) values (?, ?, 30, 0)",
                serviceId, "Consultation " + serviceId);
        jdbcTemplate.update("insert into doctor_services (doctor_id, service_id) values (?, ?)", doctorId, serviceId);
        jdbcTemplate.update("insert into users (id, email, password_hash, role) values (?, ?, ?, 'PATIENT')",
                patientUserId, patientEmail, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("A-safe-test-password-1"));
        jdbcTemplate.update("insert into patients (id, user_id, full_name) values (?, ?, 'Patient Test')", patientId, patientUserId);
        jdbcTemplate.update(
                "insert into working_hours (doctor_id, day_of_week, start_time, end_time) values (?, ?, '09:00', '12:00')",
                doctorId, date.getDayOfWeek().getValue());
        return new BookingFixture(patientId, doctorId, serviceId, startsAt, patientEmail);
    }

    private String login(String email) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "A-safe-test-password-1"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return cookieValue(response.getHeaders(), "ACCESS_TOKEN");
    }

    private String csrfToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/csrf", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return cookieValue(response.getHeaders(), "XSRF-TOKEN");
    }

    private HttpEntity<Map<String, Object>> authenticatedRequest(Map<String, Object> body, String accessToken, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "ACCESS_TOKEN=" + accessToken + "; XSRF-TOKEN=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);
        return new HttpEntity<>(body, headers);
    }

    private String cookieValue(HttpHeaders headers, String name) {
        return headers.get(HttpHeaders.SET_COOKIE).stream().filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow().split(";", 2)[0].substring(name.length() + 1);
    }

    private record BookingFixture(UUID patientId, UUID doctorId, UUID serviceId, Instant startsAt, String patientEmail) {}
}
