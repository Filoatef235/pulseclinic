package com.pulseclinic.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, max = 128)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$", message = "Password must include uppercase, lowercase, number, special character, and no spaces.")
        String password,
        @NotBlank @Size(max = 200) String fullName) {}
