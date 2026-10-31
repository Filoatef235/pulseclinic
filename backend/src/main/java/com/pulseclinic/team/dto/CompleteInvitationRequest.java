package com.pulseclinic.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteInvitationRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = 12, max = 128)
        @Pattern(regexp = "(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+", message = "Password must contain upper and lower case letters, a number, and a symbol.")
        String password) {}
