package com.pulseclinic.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record CompletePasswordResetRequest(@NotBlank @Size(max=512) String token, @NotBlank @Size(min=12,max=128) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$") String password) {}
