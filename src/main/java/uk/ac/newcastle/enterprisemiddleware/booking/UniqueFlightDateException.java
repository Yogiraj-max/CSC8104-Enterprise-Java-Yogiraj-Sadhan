package uk.ac.newcastle.enterprisemiddleware.booking;

import javax.validation.ValidationException;

/**
 * Thrown when a Booking is made for a flight + date combination that is already booked.
 */
public class UniqueFlightDateException extends ValidationException {
    public UniqueFlightDateException(String message) { super(message); }
}
