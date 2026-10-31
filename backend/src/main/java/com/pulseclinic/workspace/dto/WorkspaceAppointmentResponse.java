package com.pulseclinic.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceAppointmentResponse(
        UUID id,
        String patientName,
        String doctorName,
        String serviceName,
        Instant startsAt,
        Instant endsAt,
        String status,
        long version) {}
