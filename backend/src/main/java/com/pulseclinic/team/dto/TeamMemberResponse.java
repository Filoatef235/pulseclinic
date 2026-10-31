package com.pulseclinic.team.dto;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID userId,
        UUID profileId,
        String fullName,
        String email,
        String role,
        String professionalTitle,
        String bio,
        String phone,
        String specialty,
        boolean active,
        boolean profileComplete,
        Instant lastSignedInAt,
        String photoUrl,
        String invitationUrl) {}
