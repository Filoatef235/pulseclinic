package com.pulseclinic.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalkInPatientRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 40) String phone) {}
