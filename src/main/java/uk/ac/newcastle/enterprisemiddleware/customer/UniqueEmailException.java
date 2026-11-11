package uk.ac.newcastle.enterprisemiddleware.customer;

import javax.validation.ValidationException;

/**
 * Thrown when a Customer is created with an email address that is already in use.
 */
public class UniqueEmailException extends ValidationException {
    public UniqueEmailException(String message) { super(message); }
}
