package com.pulseclinic.workspace;

import com.pulseclinic.workspace.dto.WorkspaceAppointmentResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository {
    private final JdbcTemplate jdbc;

    public WorkspaceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<WorkspaceAppointmentResponse> appointments(LocalDate date, UUID doctorUserId, boolean allDoctors) {
        return appointments(date, doctorUserId, allDoctors, null, null, null, null);
    }
    public List<WorkspaceAppointmentResponse> appointments(LocalDate date, UUID doctorUserId, boolean allDoctors, String query, String status) {
        return appointments(date, doctorUserId, allDoctors, query, status, null, null);
    }
    public List<WorkspaceAppointmentResponse> appointments(LocalDate date, UUID doctorUserId, boolean allDoctors, String query, String status, UUID filterDoctorId, UUID serviceId) {
        String scope = allDoctors ? "" : " and d.user_id = ?";
        String dateScope = date == null ? "" : " and a.starts_at >= ? and a.starts_at < ?";
        String searchScope=query==null||query.isBlank()?"":" and (lower(p.full_name) like ? or lower(d.full_name) like ? or lower(s.name) like ?)";
        String statusScope=status==null||status.isBlank()?"":" and a.status=?";
        String doctorScope=filterDoctorId==null?"":" and a.doctor_id=?";
        String serviceScope=serviceId==null?"":" and a.service_id=?";
        java.util.ArrayList<Object> values=new java.util.ArrayList<>();
        if(date!=null){ OffsetDateTime start=date.atStartOfDay().atOffset(ZoneOffset.UTC); values.add(start); values.add(start.plusDays(1)); }
        if(!allDoctors) values.add(doctorUserId);
        if(!searchScope.isBlank()){ String term="%"+query.trim().toLowerCase()+"%"; values.add(term);values.add(term);values.add(term); }
        if(!statusScope.isBlank()) values.add(status.trim().toUpperCase());
        if(!doctorScope.isBlank()) values.add(filterDoctorId);
        if(!serviceScope.isBlank()) values.add(serviceId);
        return jdbc.query("select a.id, p.full_name patient_name, d.full_name doctor_name, s.name service_name, a.starts_at, a.ends_at, a.status, a.version "
                        + "from appointments a join patients p on p.id=a.patient_id join doctors d on d.id=a.doctor_id join services s on s.id=a.service_id "
                        + "where 1=1" + dateScope + scope + searchScope + statusScope + doctorScope + serviceScope + " order by a.starts_at",
                (rs, row) -> new WorkspaceAppointmentResponse(
                        rs.getObject("id", UUID.class), rs.getString("patient_name"), rs.getString("doctor_name"),
                        rs.getString("service_name"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("status"), rs.getLong("version")),
                values.toArray());
    }

    public List<DoctorAdminRecord> doctors() {
        return jdbc.query("select d.id, d.full_name, d.bio, s.name specialty, d.active from doctors d join specialties s on s.id=d.specialty_id order by d.full_name",
                (rs, row) -> new DoctorAdminRecord(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("bio"), rs.getString("specialty"), rs.getBoolean("active")));
    }
    public List<OperationsDoctorRecord> operationsDoctors() {
        return jdbc.query("select id, full_name from doctors order by full_name",
                (rs, row) -> new OperationsDoctorRecord(rs.getObject("id", UUID.class), rs.getString("full_name")));
    }
    public List<OperationsServiceRecord> operationsServices() {
        return jdbc.query("select id, name from services order by name",
                (rs, row) -> new OperationsServiceRecord(rs.getObject("id", UUID.class), rs.getString("name")));
    }
    public Analytics analytics(LocalDate from, LocalDate to) {
        OffsetDateTime start=from.atStartOfDay().atOffset(ZoneOffset.UTC), end=to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return jdbc.queryForObject("select count(*) total, count(*) filter(where status='PENDING') pending, count(*) filter(where status='CONFIRMED') confirmed, count(*) filter(where status='COMPLETED') completed, count(*) filter(where status='CANCELLED') cancelled, count(*) filter(where status='NO_SHOW') no_show, count(distinct doctor_id) clinicians from appointments where starts_at>=? and starts_at<?",(rs,i)->new Analytics(rs.getLong("total"),rs.getLong("pending"),rs.getLong("confirmed"),rs.getLong("completed"),rs.getLong("cancelled"),rs.getLong("no_show"),rs.getLong("clinicians")),start,end);
    }
    public List<UUID> doctorServiceIds(UUID doctorId) { return jdbc.query("select service_id from doctor_services where doctor_id=?", (rs, row) -> rs.getObject(1, UUID.class), doctorId); }
    public void replaceDoctorServices(UUID doctorId, List<UUID> serviceIds) {
        jdbc.update("delete from doctor_services where doctor_id=?", doctorId);
        for (UUID serviceId : serviceIds) jdbc.update("insert into doctor_services (doctor_id, service_id) values (?, ?)", doctorId, serviceId);
    }

    public void setDoctorActive(UUID doctorId, boolean active) { jdbc.update("update doctors set active=?, updated_at=now() where id=?", active, doctorId); }
    public void saveDoctor(UUID doctorId, String fullName, String bio) { jdbc.update("update doctors set full_name=?, bio=?, updated_at=now() where id=?", fullName, bio, doctorId); }

    public List<ServiceAdminRecord> services() { return jdbc.query("select id, name, duration_minutes, price_cents, active from services order by name", (rs, row) -> new ServiceAdminRecord(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("duration_minutes"), rs.getInt("price_cents"), rs.getBoolean("active"))); }
    public void setServiceActive(UUID serviceId, boolean active) { jdbc.update("update services set active=?, updated_at=now() where id=?", active, serviceId); }
    public void saveService(UUID id, String name, int durationMinutes, int priceCents) { jdbc.update("update services set name=?, duration_minutes=?, price_cents=?, updated_at=now() where id=?", name, durationMinutes, priceCents, id); }
    public UUID createService(String name, int durationMinutes, int priceCents) { UUID id = UUID.randomUUID(); jdbc.update("insert into services (id, name, duration_minutes, price_cents) values (?, ?, ?, ?)", id, name, durationMinutes, priceCents); return id; }
    public List<WorkingHoursRecord> workingHours(UUID doctorId) { return jdbc.query("select id, day_of_week, start_time, end_time from working_hours where doctor_id=? order by day_of_week, start_time", (rs, row) -> new WorkingHoursRecord(rs.getObject("id", UUID.class), rs.getInt("day_of_week"), rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class)), doctorId); }
    public List<TimeOffRecord> timeOff(UUID doctorId) { return jdbc.query("select id, starts_at, ends_at, reason from time_off where doctor_id=? order by starts_at", (rs, row) -> new TimeOffRecord(rs.getObject("id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class), rs.getString("reason")), doctorId); }
    public void replaceWorkingHours(UUID doctorId, int day, LocalTime start, LocalTime end) { jdbc.update("delete from working_hours where doctor_id=? and day_of_week=?", doctorId, day); jdbc.update("insert into working_hours (doctor_id, day_of_week, start_time, end_time) values (?, ?, ?, ?)", doctorId, day, start, end); }
    public UUID addTimeOff(UUID doctorId, OffsetDateTime start, OffsetDateTime end, String reason) { UUID id = UUID.randomUUID(); jdbc.update("insert into time_off (id, doctor_id, starts_at, ends_at, reason) values (?, ?, ?, ?, ?)", id, doctorId, start, end, reason); return id; }
    public void updateTimeOff(UUID id, OffsetDateTime start, OffsetDateTime end, String reason) { jdbc.update("update time_off set starts_at=?, ends_at=?, reason=? where id=?", start, end, reason, id); }
    public void deleteTimeOff(UUID id) { jdbc.update("delete from time_off where id=?", id); }

    public record DoctorAdminRecord(UUID id, String fullName, String bio, String specialty, boolean active) {}
    public record OperationsDoctorRecord(UUID id, String fullName) {}
    public record OperationsServiceRecord(UUID id, String name) {}
    public record ServiceAdminRecord(UUID id, String name, int durationMinutes, int priceCents, boolean active) {}
    public record WorkingHoursRecord(UUID id, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
    public record TimeOffRecord(UUID id, OffsetDateTime startsAt, OffsetDateTime endsAt, String reason) {}
    public record Analytics(long total,long pending,long confirmed,long completed,long cancelled,long noShow,long clinicians) {}
}
