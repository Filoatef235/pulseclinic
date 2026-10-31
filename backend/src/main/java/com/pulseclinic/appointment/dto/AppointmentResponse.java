package com.pulseclinic.appointment.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        UUID serviceId,
        Instant startsAt,
        Instant endsAt,
        String status) {}
