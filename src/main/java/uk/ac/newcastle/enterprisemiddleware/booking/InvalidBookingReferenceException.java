package uk.ac.newcastle.enterprisemiddleware.booking;

import javax.validation.ValidationException;

/**
 * Thrown when a Booking refers to a Customer or Flight that is missing or does not exist.
 * {@link #getField()} says which JSON field was wrong ("customerId" or "flightId").
 */
public class InvalidBookingReferenceException extends ValidationException {

    private final String field;

    public InvalidBookingReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
