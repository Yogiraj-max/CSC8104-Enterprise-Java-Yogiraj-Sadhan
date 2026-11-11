package uk.ac.newcastle.enterprisemiddleware.flight;

import javax.validation.ValidationException;

/**
 * Thrown when a Flight is created with a flight number that is already in use.
 */
public class UniqueFlightNumberException extends ValidationException {
    public UniqueFlightNumberException(String message) { super(message); }
}
