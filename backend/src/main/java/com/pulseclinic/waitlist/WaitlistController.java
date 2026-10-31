package com.pulseclinic.waitlist;

import com.pulseclinic.auth.AuthException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.pulseclinic.notification.EmailOutboxRepository;
import com.pulseclinic.scheduling.SlotService;
import com.pulseclinic.appointment.AppointmentRepository;
import com.pulseclinic.appointment.AppointmentSlotUnavailableException;
import com.pulseclinic.appointment.dto.AppointmentResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
@RequestMapping("/api/v1/waitlist")
public class WaitlistController {
    private final JdbcTemplate jdbc;
    private final EmailOutboxRepository emailOutbox;
    private final SlotService slots;
    private final AppointmentRepository appointments;
    public WaitlistController(JdbcTemplate jdbc, EmailOutboxRepository emailOutbox, SlotService slots, AppointmentRepository appointments) { this.jdbc=jdbc; this.emailOutbox=emailOutbox; this.slots=slots; this.appointments=appointments; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UUID join(@Valid @RequestBody Request request, @AuthenticationPrincipal Jwt jwt) {
        requirePatient(jwt); UUID patient=jdbc.query("select id from patients where user_id=?",(rs,i)->rs.getObject(1,UUID.class),UUID.fromString(jwt.getSubject())).stream().findFirst().orElseThrow(()->new AuthException("PATIENT_NOT_FOUND","Patient profile not found."));
        UUID id=UUID.randomUUID(); jdbc.update("insert into waitlist_entries (id,patient_id,service_id,doctor_id,requested_date,contact_preference) values (?,?,?,?,?,?)",id,patient,request.serviceId(),request.doctorId(),request.requestedDate(),request.contactPreference()); return id;
    }
    @GetMapping public List<Item> mine(@AuthenticationPrincipal Jwt jwt) { requirePatient(jwt); return jdbc.query("select w.id,w.service_id,w.offered_doctor_id,s.name service_name,d.full_name doctor_name,w.requested_date,w.status,w.contact_preference,w.offered_starts_at from waitlist_entries w join patients p on p.id=w.patient_id join services s on s.id=w.service_id left join doctors d on d.id=coalesce(w.offered_doctor_id,w.doctor_id) where p.user_id=? order by w.created_at desc",(rs,i)->new Item(rs.getObject("id",UUID.class),rs.getObject("service_id",UUID.class),rs.getObject("offered_doctor_id",UUID.class),rs.getString("service_name"),rs.getString("doctor_name"),rs.getObject("requested_date",LocalDate.class),rs.getString("status"),rs.getString("contact_preference"),rs.getObject("offered_starts_at",java.time.OffsetDateTime.class)==null?null:rs.getObject("offered_starts_at",java.time.OffsetDateTime.class).toInstant()),UUID.fromString(jwt.getSubject())); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { requirePatient(jwt); jdbc.update("update waitlist_entries set status='CANCELLED',updated_at=now() where id=? and patient_id in (select id from patients where user_id=?) and status in ('ACTIVE','OFFERED')",id,UUID.fromString(jwt.getSubject())); }
    @PostMapping("/{id}/accept") @Transactional public AppointmentResponse accept(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { requirePatient(jwt); UUID userId=UUID.fromString(jwt.getSubject()); OfferBooking booking=jdbc.query("select w.patient_id,w.service_id,w.offered_doctor_id,w.offered_starts_at from waitlist_entries w join patients p on p.id=w.patient_id where w.id=? and p.user_id=? and w.status='OFFERED'",(rs,i)->new OfferBooking(rs.getObject("patient_id",UUID.class),rs.getObject("service_id",UUID.class),rs.getObject("offered_doctor_id",UUID.class),rs.getObject("offered_starts_at",java.time.OffsetDateTime.class).toInstant()),id,userId).stream().findFirst().orElseThrow(()->new AuthException("WAITLIST_OFFER_NOT_AVAILABLE","This offer is no longer available.")); if(booking.doctorId()==null||!slots.isBookable(booking.doctorId(),booking.serviceId(),booking.startsAt())) throw new AppointmentSlotUnavailableException(); int minutes=jdbc.queryForObject("select duration_minutes from services where id=?",Integer.class,booking.serviceId()); try { AppointmentResponse result=appointments.create(booking.patientId(),booking.doctorId(),booking.serviceId(),booking.startsAt(),booking.startsAt().plusSeconds(minutes*60L),"Booked from waitlist offer"); jdbc.update("update waitlist_entries set status='FULFILLED',updated_at=now() where id=?",id); return result; } catch(DataIntegrityViolationException ex){ throw new AppointmentSlotUnavailableException(); } }
    @GetMapping("/operations") public List<OperationsItem> operations(@AuthenticationPrincipal Jwt jwt) { requireOperations(jwt); return jdbc.query("select w.id,p.full_name patient_name,u.email,w.service_id,w.doctor_id,s.name service_name,d.full_name doctor_name,w.requested_date,w.status,w.contact_preference from waitlist_entries w join patients p on p.id=w.patient_id left join users u on u.id=p.user_id join services s on s.id=w.service_id left join doctors d on d.id=w.doctor_id where w.status in ('ACTIVE','OFFERED') order by w.created_at",(rs,i)->new OperationsItem(rs.getObject("id",UUID.class),rs.getString("patient_name"),rs.getString("email"),rs.getObject("service_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("service_name"),rs.getString("doctor_name"),rs.getObject("requested_date",LocalDate.class),rs.getString("status"),rs.getString("contact_preference"))); }
    @PostMapping("/{id}/offer") @ResponseStatus(HttpStatus.NO_CONTENT) public void offer(@PathVariable UUID id,@Valid @RequestBody OfferRequest request,@AuthenticationPrincipal Jwt jwt) { requireOperations(jwt); OperationsItem item=jdbc.query("select w.id,p.full_name patient_name,u.email,w.service_id,w.doctor_id,s.name service_name,d.full_name doctor_name,w.requested_date,w.status,w.contact_preference from waitlist_entries w join patients p on p.id=w.patient_id left join users u on u.id=p.user_id join services s on s.id=w.service_id left join doctors d on d.id=w.doctor_id where w.id=? and w.status='ACTIVE'",(rs,i)->new OperationsItem(rs.getObject("id",UUID.class),rs.getString("patient_name"),rs.getString("email"),rs.getObject("service_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("service_name"),rs.getString("doctor_name"),rs.getObject("requested_date",LocalDate.class),rs.getString("status"),rs.getString("contact_preference")),id).stream().findFirst().orElseThrow(()->new AuthException("WAITLIST_NOT_AVAILABLE","This waitlist request is not available for an offer.")); UUID doctorId=request.doctorId()==null?item.doctorId():request.doctorId(); if(doctorId==null||!slots.isBookable(doctorId,item.serviceId(),request.startsAt())) throw new AuthException("WAITLIST_SLOT_UNAVAILABLE","That opening is no longer available."); jdbc.update("update waitlist_entries set status='OFFERED',offered_starts_at=?,offered_doctor_id=?,offered_at=now(),updated_at=now() where id=? and status='ACTIVE'",java.time.OffsetDateTime.ofInstant(request.startsAt(),java.time.ZoneOffset.UTC),doctorId,id); if(item.email()!=null) emailOutbox.queueWaitlistOffer(item.email(),id); }
    private void requirePatient(Jwt jwt) { if (!jwt.getClaimAsStringList("roles").contains("ROLE_PATIENT")) throw new AccessDeniedException("Patient access is required."); }
    private void requireOperations(Jwt jwt) { if (!jwt.getClaimAsStringList("roles").stream().anyMatch(role->role.equals("ROLE_STAFF")||role.equals("ROLE_ADMIN"))) throw new AccessDeniedException("Staff or admin access is required."); }
    public record Request(@NotNull UUID serviceId, UUID doctorId, LocalDate requestedDate, String contactPreference) { public String contactPreference(){ return "PHONE".equals(contactPreference)?"PHONE":"EMAIL"; } }
    public record Item(UUID id,UUID serviceId,UUID offeredDoctorId,String serviceName,String doctorName,LocalDate requestedDate,String status,String contactPreference,Instant offeredStartsAt) {}
    public record OperationsItem(UUID id,String patientName,String email,UUID serviceId,UUID doctorId,String serviceName,String doctorName,LocalDate requestedDate,String status,String contactPreference) {}
    public record OfferRequest(@NotNull Instant startsAt, UUID doctorId) {}
    record OfferBooking(UUID patientId,UUID serviceId,UUID doctorId,Instant startsAt) {}
}
