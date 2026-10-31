package com.pulseclinic.appointment;

public class AppointmentSlotUnavailableException extends RuntimeException {
    public AppointmentSlotUnavailableException() {
        super("The selected appointment slot is no longer available.");
    }
}
