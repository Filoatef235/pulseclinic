package com.pulseclinic.appointment.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record UpdateAppointmentStatusRequest(@NotBlank String status, @NotNull Long version) {}
