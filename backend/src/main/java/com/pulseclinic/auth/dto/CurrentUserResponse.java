package com.pulseclinic.auth.dto;
import java.util.UUID;
public record CurrentUserResponse(UUID id, String email, String role, UUID patientId) {}
