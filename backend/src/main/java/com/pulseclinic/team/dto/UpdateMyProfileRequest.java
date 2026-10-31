package com.pulseclinic.team.dto;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(@Size(max = 3000) String bio) {}
