package com.pulseclinic.appointment;

import com.pulseclinic.appointment.dto.AppointmentResponse;
import com.pulseclinic.appointment.dto.CreateAppointmentRequest;
import com.pulseclinic.scheduling.SchedulingRepository;
import com.pulseclinic.scheduling.SlotService;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import com.pulseclinic.notification.EmailOutboxRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final SchedulingRepository schedulingRepository;
    private final SlotService slotService;
    private final EmailOutboxRepository emailOutbox;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            SchedulingRepository schedulingRepository,
            SlotService slotService, EmailOutboxRepository emailOutbox) {
        this.appointmentRepository = appointmentRepository;
        this.schedulingRepository = schedulingRepository;
        this.slotService = slotService;
        this.emailOutbox = emailOutbox;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request, UUID actorUserId, String role) {
        if ("PATIENT".equals(role) && !schedulingRepository.patientBelongsTo(request.patientId(), actorUserId)) {
            throw new AccessDeniedException("Patients can only book appointments for themselves.");
        }
        if (!"PATIENT".equals(role) && !"STAFF".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("This role cannot create appointments.");
        }
        if (!schedulingRepository.patientExists(request.patientId())) {
            throw new InvalidAppointmentRequestException("Patient not found.");
        }
        if (!slotService.isBookable(request.doctorId(), request.serviceId(), request.startsAt())) {
            throw new AppointmentSlotUnavailableException();
        }
        Instant endsAt = request.startsAt().plusSeconds(
                schedulingRepository.serviceDurationMinutes(request.serviceId()) * 60L);
        try {
            AppointmentResponse appointment = appointmentRepository.create(
                    request.patientId(), request.doctorId(), request.serviceId(), request.startsAt(), endsAt, request.notes());
            emailOutbox.queue(userEmail(actorUserId), "APPOINTMENT_BOOKED", appointment.id());
            return appointment;
        } catch (DataIntegrityViolationException ex) {
            throw new AppointmentSlotUnavailableException();
        }
    }

    public java.util.List<com.pulseclinic.appointment.dto.AppointmentListItem> mine(UUID userId, String role) {
        if (!"PATIENT".equals(role)) throw new AccessDeniedException("Use the role-specific appointment workspace.");
        return appointmentRepository.forPatient(userId);
    }
    public com.pulseclinic.appointment.dto.AppointmentDetailsResponse details(UUID appointmentId, UUID userId, String role) {
        if (!"PATIENT".equals(role)) throw new AccessDeniedException("Use the role-specific appointment workspace.");
        return appointmentRepository.detailsForPatient(appointmentId, userId).orElseThrow(() -> new InvalidAppointmentRequestException("Appointment not found."));
    }
    public String calendar(UUID appointmentId, UUID userId, String role) {
        com.pulseclinic.appointment.dto.AppointmentDetailsResponse detail = details(appointmentId, userId, role);
        if (!"CONFIRMED".equals(detail.status())) throw new InvalidAppointmentStateException("Only confirmed appointments can be added to a calendar.");
        String uid = detail.id() + "@pulseclinic.local";
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//PulseClinic//Appointments//EN\r\nCALSCALE:GREGORIAN\r\nBEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:" + ics(Instant.now()) + "\r\nDTSTART:" + ics(detail.startsAt()) + "\r\nDTEND:" + ics(detail.endsAt()) + "\r\nSUMMARY:" + escape("PulseClinic — " + detail.serviceName()) + "\r\nDESCRIPTION:" + escape("Appointment with " + detail.doctorName() + " (" + detail.specialty() + ")") + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
    }
    private String ics(Instant value) { return java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(value); }
    private String escape(String value) { return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n"); }

    @Transactional
    public void cancel(UUID appointmentId, long version, UUID actorUserId, String role) {
        AppointmentRepository.AppointmentDetail appointment = appointmentRepository.appointment(appointmentId);
        requirePatientOwner(appointment, actorUserId, role);
        if (!appointment.status().equals("PENDING") && !appointment.status().equals("CONFIRMED")) throw new InvalidAppointmentStateException("This appointment cannot be cancelled.");
        if ("PATIENT".equals(role) && appointment.startsAt().isBefore(Instant.now().plusSeconds(cancellationCutoffHours() * 3600L))) throw new InvalidAppointmentStateException("This appointment is inside the cancellation cutoff.");
        if (!appointmentRepository.setStatus(appointmentId, appointment.status(), "CANCELLED", version, actorUserId)) throw new InvalidAppointmentStateException("This appointment changed. Refresh and try again.");
        if (appointment.patientEmail() != null) emailOutbox.queue(appointment.patientEmail(), "APPOINTMENT_CANCELLED", appointmentId);
    }

    @Transactional
    public AppointmentResponse reschedule(UUID appointmentId, com.pulseclinic.appointment.dto.RescheduleAppointmentRequest request, UUID actorUserId, String role) {
        AppointmentRepository.AppointmentDetail appointment = appointmentRepository.appointment(appointmentId);
        requirePatientOwner(appointment, actorUserId, role);
        if (!appointment.status().equals("PENDING") && !appointment.status().equals("CONFIRMED")) throw new InvalidAppointmentStateException("This appointment cannot be rescheduled.");
        if ("PATIENT".equals(role) && appointment.startsAt().isBefore(Instant.now().plusSeconds(cancellationCutoffHours() * 3600L))) throw new InvalidAppointmentStateException("This appointment is inside the cancellation cutoff.");
        if (!slotService.isBookable(appointment.doctorId(), appointment.serviceId(), request.startsAt())) throw new AppointmentSlotUnavailableException();
        if (!appointmentRepository.setStatus(appointmentId, appointment.status(), "CANCELLED", request.version(), actorUserId)) throw new InvalidAppointmentStateException("This appointment changed. Refresh and try again.");
        Instant endsAt = request.startsAt().plusSeconds(schedulingRepository.serviceDurationMinutes(appointment.serviceId()) * 60L);
        try {
            AppointmentResponse result = appointmentRepository.create(appointment.patientId(), appointment.doctorId(), appointment.serviceId(), request.startsAt(), endsAt, null);
            if (appointment.patientEmail() != null) emailOutbox.queue(appointment.patientEmail(), "APPOINTMENT_RESCHEDULED", result.id());
            return result;
        } catch (DataIntegrityViolationException ex) { throw new AppointmentSlotUnavailableException(); }
    }

    @Transactional
    public void updateStatus(UUID appointmentId, com.pulseclinic.appointment.dto.UpdateAppointmentStatusRequest request, UUID actorUserId, String role) {
        AppointmentRepository.AppointmentDetail appointment = appointmentRepository.appointment(appointmentId);
        String next = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if ("DOCTOR".equals(role) && !appointment.doctorUserId().equals(actorUserId)) throw new AccessDeniedException("Doctors can only update their own appointments.");
        if ("DOCTOR".equals(role) && !(next.equals("COMPLETED") || next.equals("NO_SHOW"))) throw new AccessDeniedException("Doctors can only mark appointments completed or no-show.");
        if (!("DOCTOR".equals(role) || "STAFF".equals(role) || "ADMIN".equals(role))) throw new AccessDeniedException("This role cannot update appointment status.");
        if (!allowed(appointment.status(), next)) throw new InvalidAppointmentStateException("This status transition is not allowed.");
        if (!appointmentRepository.setStatus(appointmentId, appointment.status(), next, request.version(), actorUserId)) throw new InvalidAppointmentStateException("This appointment changed. Refresh and try again.");
        if (appointment.patientEmail() != null) emailOutbox.queue(appointment.patientEmail(), "APPOINTMENT_" + next, appointmentId);
    }

    private void requirePatientOwner(AppointmentRepository.AppointmentDetail appointment, UUID userId, String role) {
        if ("PATIENT".equals(role) && !schedulingRepository.patientBelongsTo(appointment.patientId(), userId)) throw new AccessDeniedException("Patients can only manage their own appointments.");
        if (!("PATIENT".equals(role) || "STAFF".equals(role) || "ADMIN".equals(role))) throw new AccessDeniedException("This role cannot manage appointments.");
    }
    private boolean allowed(String from, String to) { return (from.equals("PENDING") && (to.equals("CONFIRMED") || to.equals("CANCELLED"))) || (from.equals("CONFIRMED") && (to.equals("COMPLETED") || to.equals("NO_SHOW") || to.equals("CANCELLED"))); }
    private int cancellationCutoffHours() { return schedulingRepository.cancelCutoffHours(); }
    private String userEmail(UUID userId) { return schedulingRepository.userEmail(userId); }
}
