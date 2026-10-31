package com.pulseclinic.appointment.dto;
import jakarta.validation.constraints.NotNull;
public record CancelAppointmentRequest(@NotNull Long version) {}
