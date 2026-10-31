package com.pulseclinic.appointment;

import com.pulseclinic.appointment.dto.AppointmentResponse;
import com.pulseclinic.appointment.dto.CreateAppointmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody CreateAppointmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return appointmentService.create(request, actor(jwt), role(jwt));
    }
    @GetMapping
    public java.util.List<com.pulseclinic.appointment.dto.AppointmentListItem> mine(@AuthenticationPrincipal Jwt jwt) { return appointmentService.mine(actor(jwt), role(jwt)); }
    @GetMapping("/{id}")
    public com.pulseclinic.appointment.dto.AppointmentDetailsResponse details(@PathVariable java.util.UUID id, @AuthenticationPrincipal Jwt jwt) { return appointmentService.details(id, actor(jwt), role(jwt)); }
    @GetMapping("/{id}/calendar")
    public ResponseEntity<String> calendar(@PathVariable java.util.UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).header("Content-Disposition", "attachment; filename=\"pulseclinic-appointment.ics\"").body(appointmentService.calendar(id, actor(jwt), role(jwt)));
    }
    @PostMapping("/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable java.util.UUID id, @Valid @RequestBody com.pulseclinic.appointment.dto.CancelAppointmentRequest request, @AuthenticationPrincipal Jwt jwt) { appointmentService.cancel(id, request.version(), actor(jwt), role(jwt)); }
    @PostMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@PathVariable java.util.UUID id, @Valid @RequestBody com.pulseclinic.appointment.dto.RescheduleAppointmentRequest request, @AuthenticationPrincipal Jwt jwt) { return appointmentService.reschedule(id, request, actor(jwt), role(jwt)); }
    @PatchMapping("/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@PathVariable java.util.UUID id, @Valid @RequestBody com.pulseclinic.appointment.dto.UpdateAppointmentStatusRequest request, @AuthenticationPrincipal Jwt jwt) { appointmentService.updateStatus(id, request, actor(jwt), role(jwt)); }
    private java.util.UUID actor(Jwt jwt) { return java.util.UUID.fromString(jwt.getSubject()); }
    private String role(Jwt jwt) { return jwt.getClaimAsStringList("roles").get(0).replace("ROLE_", ""); }
}
