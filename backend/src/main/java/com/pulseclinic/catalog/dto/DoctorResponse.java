package com.pulseclinic.catalog.dto;
import java.util.UUID;
public record DoctorResponse(UUID id, String fullName, String specialty, String bio, String photoUrl) {}
