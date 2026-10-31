package com.pulseclinic.scheduling;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SchedulingRepository {
    private final JdbcTemplate jdbcTemplate;

    public SchedulingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ClinicSettings clinicSettings() {
        return jdbcTemplate.queryForObject(
                "select timezone, slot_length_minutes, booking_horizon_days from clinic_settings where id = 1",
                (rs, rowNum) -> new ClinicSettings(
                        java.time.ZoneId.of(rs.getString("timezone")),
                        rs.getInt("slot_length_minutes"),
                        rs.getInt("booking_horizon_days")));
    }
    public int cancelCutoffHours() { return jdbcTemplate.queryForObject("select cancel_cutoff_hours from clinic_settings where id=1", Integer.class); }
    public String userEmail(UUID userId) { return jdbcTemplate.queryForObject("select email from users where id=?", String.class, userId); }

    public boolean patientExists(UUID patientId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from patients where id = ?)", Boolean.class, patientId));
    }

    public boolean patientBelongsTo(UUID patientId, UUID userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from patients where id = ? and user_id = ?)", Boolean.class, patientId, userId));
    }

    public boolean doctorCanPerform(UUID doctorId, UUID serviceId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from doctors d join doctor_services ds on ds.doctor_id = d.id "
                        + "join services s on s.id = ds.service_id "
                        + "where d.id = ? and ds.service_id = ? and d.active and s.active)",
                Boolean.class,
                doctorId,
                serviceId));
    }

    public int serviceDurationMinutes(UUID serviceId) {
        Integer duration = jdbcTemplate.queryForObject(
                "select duration_minutes from services where id = ? and active", Integer.class, serviceId);
        if (duration == null) {
            throw new IllegalArgumentException("Service not found");
        }
        return duration;
    }

    public List<WorkingPeriod> workingPeriods(UUID doctorId, int isoDayOfWeek) {
        return jdbcTemplate.query(
                "select start_time, end_time from working_hours where doctor_id = ? and day_of_week = ? "
                        + "order by start_time",
                (rs, rowNum) -> new WorkingPeriod(
                        rs.getObject("start_time", Time.class).toLocalTime(),
                        rs.getObject("end_time", Time.class).toLocalTime()),
                doctorId,
                isoDayOfWeek);
    }

    public boolean overlapsTimeOff(UUID doctorId, Instant startsAt, Instant endsAt) {
        OffsetDateTime start = OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC);
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from time_off where (doctor_id = ? or doctor_id is null) "
                        + "and starts_at < ? and ends_at > ?)",
                Boolean.class,
                doctorId,
                end,
                start));
    }

    public boolean overlapsActiveAppointment(UUID doctorId, Instant startsAt, Instant endsAt) {
        OffsetDateTime start = OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC);
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from appointments where doctor_id = ? "
                        + "and status in ('PENDING', 'CONFIRMED') and starts_at < ? and ends_at > ?)",
                Boolean.class,
                doctorId,
                end,
                start));
    }

    public record WorkingPeriod(LocalTime startsAt, LocalTime endsAt) {}
}
