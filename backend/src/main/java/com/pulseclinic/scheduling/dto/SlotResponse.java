package com.pulseclinic.scheduling.dto;

import java.time.Instant;

public record SlotResponse(Instant startsAt, Instant endsAt) {}
