package com.pulseclinic.workspace;

import com.pulseclinic.workspace.dto.WorkspaceAppointmentResponse;
import com.pulseclinic.appointment.InvalidAppointmentRequestException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkspaceController {
    private final WorkspaceRepository repository;

    public WorkspaceController(WorkspaceRepository repository) { this.repository = repository; }

    @GetMapping("/workspace/appointments")
    public List<WorkspaceAppointmentResponse> appointments(@RequestParam(required = false) LocalDate date, @RequestParam(required=false) String query, @RequestParam(required=false) String status, @RequestParam(required=false) UUID doctorId, @RequestParam(required=false) UUID serviceId, @AuthenticationPrincipal Jwt jwt) {
        String role = role(jwt);
        if ("DOCTOR".equals(role)) return repository.appointments(date, UUID.fromString(jwt.getSubject()), false, query, status, null, serviceId);
        if ("STAFF".equals(role) || "ADMIN".equals(role)) return repository.appointments(date, null, true, query, status, doctorId, serviceId);
        throw new AccessDeniedException("This workspace is not available to patients.");
    }
    @GetMapping("/workspace/filter-options")
    public FilterOptions filterOptions(@AuthenticationPrincipal Jwt jwt) {
        String role = role(jwt);
        if (!"DOCTOR".equals(role) && !"STAFF".equals(role) && !"ADMIN".equals(role)) throw new AccessDeniedException("This workspace is not available to patients.");
        return new FilterOptions(repository.operationsDoctors(), repository.operationsServices());
    }
    @GetMapping("/admin/analytics")
    public WorkspaceRepository.Analytics analytics(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); LocalDate end=to==null?LocalDate.now():to; LocalDate start=from==null?end.minusDays(29):from; if(start.isAfter(end)||start.plusDays(366).isBefore(end)) throw new InvalidAppointmentRequestException("Choose a date range up to one year."); return repository.analytics(start,end); }

    @GetMapping("/admin/doctors")
    public List<WorkspaceRepository.DoctorAdminRecord> doctors(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return repository.doctors();
    }

    @PutMapping("/admin/doctors/{id}/active")
    public void setDoctorActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        repository.setDoctorActive(id, request.active());
    }
    @PutMapping("/admin/doctors/{id}") public void saveDoctor(@PathVariable UUID id, @RequestBody DoctorRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); repository.saveDoctor(id, request.fullName().trim(), request.bio().trim()); }

    @GetMapping("/admin/services") public List<WorkspaceRepository.ServiceAdminRecord> services(@AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); return repository.services(); }
    @PutMapping("/admin/services/{id}/active") public void setServiceActive(@PathVariable UUID id, @RequestBody ActiveRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); repository.setServiceActive(id, request.active()); }
    @PutMapping("/admin/services/{id}") public void saveService(@PathVariable UUID id, @RequestBody ServiceRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); repository.saveService(id, request.name().trim(), request.durationMinutes(), request.priceCents()); }
    @PostMapping("/admin/services") public UUID createService(@RequestBody ServiceRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); return repository.createService(request.name().trim(), request.durationMinutes(), request.priceCents()); }
    @GetMapping("/admin/doctors/{id}/working-hours") public List<WorkspaceRepository.WorkingHoursRecord> workingHours(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); return repository.workingHours(id); }
    @GetMapping("/admin/doctors/{id}/services") public List<UUID> doctorServices(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); return repository.doctorServiceIds(id); }
    @PutMapping("/admin/doctors/{id}/services") public void replaceDoctorServices(@PathVariable UUID id, @RequestBody DoctorServicesRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); repository.replaceDoctorServices(id, request.serviceIds()); }
    @GetMapping("/admin/doctors/{id}/time-off") public List<WorkspaceRepository.TimeOffRecord> timeOff(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); return repository.timeOff(id); }
    @PutMapping("/admin/doctors/{id}/working-hours") public void replaceWorkingHours(@PathVariable UUID id, @RequestBody WorkingHoursRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7 || !request.endTime().isAfter(request.startTime())) throw new InvalidAppointmentRequestException("Working hours must end after they start."); repository.replaceWorkingHours(id, request.dayOfWeek(), request.startTime(), request.endTime()); }
    @PostMapping("/admin/doctors/{id}/time-off") public UUID addTimeOff(@PathVariable UUID id, @RequestBody TimeOffRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); if (!request.endsAt().isAfter(request.startsAt())) throw new InvalidAppointmentRequestException("Time off must end after it starts."); return repository.addTimeOff(id, request.startsAt(), request.endsAt(), request.reason()); }
    @PutMapping("/admin/time-off/{id}") public void updateTimeOff(@PathVariable UUID id, @RequestBody TimeOffRequest request, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); if (!request.endsAt().isAfter(request.startsAt())) throw new InvalidAppointmentRequestException("Time off must end after it starts."); repository.updateTimeOff(id, request.startsAt(), request.endsAt(), request.reason()); }
    @DeleteMapping("/admin/time-off/{id}") public void deleteTimeOff(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { requireAdmin(jwt); repository.deleteTimeOff(id); }

    private void requireAdmin(Jwt jwt) { if (!"ADMIN".equals(role(jwt))) throw new AccessDeniedException("Admin access is required."); }
    private String role(Jwt jwt) { return jwt.getClaimAsStringList("roles").get(0).replace("ROLE_", ""); }
    public record ActiveRequest(boolean active) {}
    public record ServiceRequest(String name, int durationMinutes, int priceCents) {}
    public record WorkingHoursRequest(int dayOfWeek, LocalTime startTime, LocalTime endTime) {}
    public record TimeOffRequest(OffsetDateTime startsAt, OffsetDateTime endsAt, String reason) {}
    public record DoctorServicesRequest(List<UUID> serviceIds) {}
    public record DoctorRequest(String fullName, String bio) {}
    public record FilterOptions(List<WorkspaceRepository.OperationsDoctorRecord> doctors, List<WorkspaceRepository.OperationsServiceRecord> services) {}
}
