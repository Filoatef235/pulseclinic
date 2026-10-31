package com.pulseclinic.appointment.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AppointmentDetailsResponse(UUID id, String doctorName, String specialty, String serviceName, Instant startsAt, Instant endsAt, String status, long version, List<StatusEvent> history) {
    public record StatusEvent(String fromStatus, String toStatus, Instant changedAt, String changedByRole) {}
}
