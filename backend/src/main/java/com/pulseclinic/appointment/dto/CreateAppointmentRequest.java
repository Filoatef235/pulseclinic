package com.pulseclinic.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        @NotNull UUID serviceId,
        @NotNull Instant startsAt,
        @Size(max = 1000) String notes) {}
