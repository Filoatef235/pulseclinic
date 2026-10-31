package com.pulseclinic.appointment.dto;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record RescheduleAppointmentRequest(@NotNull Instant startsAt, @NotNull Long version) {}
