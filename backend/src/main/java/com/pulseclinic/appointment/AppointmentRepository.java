package com.pulseclinic.appointment;

import com.pulseclinic.appointment.dto.AppointmentResponse;
import com.pulseclinic.appointment.dto.AppointmentListItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppointmentResponse create(UUID patientId, UUID doctorId, UUID serviceId, Instant startsAt, Instant endsAt, String notes) {
        return jdbcTemplate.queryForObject(
                "insert into appointments (patient_id, doctor_id, service_id, starts_at, ends_at, notes) "
                        + "values (?, ?, ?, ?, ?, ?) "
                        + "returning id, patient_id, doctor_id, service_id, starts_at, ends_at, status",
                (rs, rowNum) -> new AppointmentResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("patient_id", UUID.class),
                        rs.getObject("doctor_id", UUID.class),
                        rs.getObject("service_id", UUID.class),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                        rs.getString("status")),
                patientId,
                doctorId,
                serviceId,
                OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC),
                notes);
    }

    public List<AppointmentListItem> forPatient(UUID userId) {
        return jdbcTemplate.query("select a.id, a.doctor_id, a.service_id, d.full_name doctor_name, s.name service_name, a.starts_at, a.ends_at, a.status, a.version from appointments a join patients p on p.id=a.patient_id join doctors d on d.id=a.doctor_id join services s on s.id=a.service_id where p.user_id=? order by a.starts_at desc", (rs, i) -> new AppointmentListItem(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getObject("service_id", UUID.class), rs.getString("doctor_name"), rs.getString("service_name"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(), rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("status"), rs.getLong("version")), userId);
    }
    public AppointmentDetail appointment(UUID id) {
        return jdbcTemplate.query("select a.id, a.patient_id, a.doctor_id, a.service_id, a.starts_at, a.ends_at, a.status, a.version, u.email patient_email, d.user_id doctor_user_id from appointments a join patients p on p.id=a.patient_id left join users u on u.id=p.user_id join doctors d on d.id=a.doctor_id where a.id=?", (rs,i) -> new AppointmentDetail(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getObject("service_id",UUID.class),rs.getObject("starts_at",OffsetDateTime.class).toInstant(),rs.getObject("ends_at",OffsetDateTime.class).toInstant(),rs.getString("status"),rs.getLong("version"),rs.getString("patient_email"),rs.getObject("doctor_user_id",UUID.class)),id).stream().findFirst().orElseThrow(() -> new InvalidAppointmentRequestException("Appointment not found."));
    }
    public java.util.Optional<com.pulseclinic.appointment.dto.AppointmentDetailsResponse> detailsForPatient(UUID appointmentId, UUID userId) {
        return jdbcTemplate.query("select a.id, d.full_name doctor_name, sp.name specialty, s.name service_name, a.starts_at, a.ends_at, a.status, a.version from appointments a join patients p on p.id=a.patient_id join doctors d on d.id=a.doctor_id join specialties sp on sp.id=d.specialty_id join services s on s.id=a.service_id where a.id=? and p.user_id=?", (rs, i) -> new com.pulseclinic.appointment.dto.AppointmentDetailsResponse(rs.getObject("id", UUID.class), rs.getString("doctor_name"), rs.getString("specialty"), rs.getString("service_name"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(), rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("status"), rs.getLong("version"), history(appointmentId)), appointmentId, userId).stream().findFirst();
    }
    private java.util.List<com.pulseclinic.appointment.dto.AppointmentDetailsResponse.StatusEvent> history(UUID appointmentId) {
        return jdbcTemplate.query("select h.from_status, h.to_status, h.changed_at, u.role from appointment_status_history h left join users u on u.id=h.changed_by where h.appointment_id=? order by h.changed_at", (rs, i) -> new com.pulseclinic.appointment.dto.AppointmentDetailsResponse.StatusEvent(rs.getString("from_status"), rs.getString("to_status"), rs.getObject("changed_at", OffsetDateTime.class).toInstant(), rs.getString("role")), appointmentId);
    }
    public boolean setStatus(UUID id, String from, String to, long version, UUID actor) {
        int changed = jdbcTemplate.update("update appointments set status=?, version=version+1, updated_at=now() where id=? and status=? and version=?", to, id, from, version);
        if (changed == 1) jdbcTemplate.update("insert into appointment_status_history (appointment_id, from_status, to_status, changed_by) values (?, ?, ?, ?)", id, from, to, actor);
        return changed == 1;
    }
    public record AppointmentDetail(UUID id, UUID patientId, UUID doctorId, UUID serviceId, Instant startsAt, Instant endsAt, String status, long version, String patientEmail, UUID doctorUserId) {}
}
