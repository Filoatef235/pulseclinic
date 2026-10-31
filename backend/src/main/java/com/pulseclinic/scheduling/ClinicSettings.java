package com.pulseclinic.scheduling;

import java.time.ZoneId;

public record ClinicSettings(ZoneId zoneId, int slotLengthMinutes, int bookingHorizonDays) {}
