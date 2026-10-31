package com.pulseclinic.scheduling;

import com.pulseclinic.appointment.InvalidAppointmentRequestException;
import com.pulseclinic.scheduling.dto.SlotResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SlotService {
    private final SchedulingRepository repository;

    public SlotService(SchedulingRepository repository) {
        this.repository = repository;
    }

    public List<SlotResponse> slots(UUID doctorId, UUID serviceId, LocalDate date) {
        ClinicSettings settings = repository.clinicSettings();
        validateDate(date, settings);
        if (!repository.doctorCanPerform(doctorId, serviceId)) {
            throw new InvalidAppointmentRequestException("Doctor or service is unavailable.");
        }
        int durationMinutes = repository.serviceDurationMinutes(serviceId);
        List<SlotResponse> slots = new ArrayList<>();
        for (SchedulingRepository.WorkingPeriod period : repository.workingPeriods(
                doctorId, date.getDayOfWeek().getValue())) {
            ZonedDateTime cursor = ZonedDateTime.of(date, period.startsAt(), settings.zoneId());
            ZonedDateTime closing = ZonedDateTime.of(date, period.endsAt(), settings.zoneId());
            while (!cursor.plusMinutes(durationMinutes).isAfter(closing)) {
                Instant startsAt = cursor.toInstant();
                Instant endsAt = cursor.plusMinutes(durationMinutes).toInstant();
                if (!repository.overlapsTimeOff(doctorId, startsAt, endsAt)
                        && !repository.overlapsActiveAppointment(doctorId, startsAt, endsAt)) {
                    slots.add(new SlotResponse(startsAt, endsAt));
                }
                cursor = cursor.plusMinutes(settings.slotLengthMinutes());
            }
        }
        return slots;
    }

    public boolean isBookable(UUID doctorId, UUID serviceId, Instant startsAt) {
        ClinicSettings settings = repository.clinicSettings();
        LocalDate date = startsAt.atZone(settings.zoneId()).toLocalDate();
        return slots(doctorId, serviceId, date).stream().anyMatch(slot -> slot.startsAt().equals(startsAt));
    }

    private void validateDate(LocalDate date, ClinicSettings settings) {
        LocalDate today = LocalDate.now(settings.zoneId());
        if (date.isBefore(today) || date.isAfter(today.plusDays(settings.bookingHorizonDays()))) {
            throw new InvalidAppointmentRequestException("Date is outside the booking horizon.");
        }
    }
}
