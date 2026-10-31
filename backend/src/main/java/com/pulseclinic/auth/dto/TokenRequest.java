package com.pulseclinic.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record TokenRequest(@NotBlank @Size(max = 512) String token) {}
