package com.pulseclinic.team.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTeamMemberRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "DOCTOR|STAFF") String role,
        @Size(max = 120) String professionalTitle,
        @Size(max = 3000) String bio,
        @Size(max = 40) String phone,
        UUID specialtyId) {}
