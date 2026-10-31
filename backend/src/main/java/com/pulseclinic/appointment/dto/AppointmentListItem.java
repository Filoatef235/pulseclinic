package com.pulseclinic.appointment.dto;
import java.time.Instant;
import java.util.UUID;
public record AppointmentListItem(UUID id, UUID doctorId, UUID serviceId, String doctorName, String serviceName, Instant startsAt, Instant endsAt, String status, long version) {}
