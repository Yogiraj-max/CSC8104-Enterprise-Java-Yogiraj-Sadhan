package uk.ac.newcastle.enterprisemiddleware.flight;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates a Flight: Bean Validation annotations (including departure != destination) and flight number uniqueness.
 */
@ApplicationScoped
public class FlightValidator {

    @Inject
    Validator validator;

    @Inject
    FlightRepository crud;

    void validateFlight(Flight flight) throws ConstraintViolationException, UniqueFlightNumberException {
        Set<ConstraintViolation<Flight>> violations = validator.validate(flight);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<ConstraintViolation<?>>(violations));
        }
        if (flightNumberAlreadyExists(flight.getFlightNumber())) {
            throw new UniqueFlightNumberException("Unique Flight Number Violation");
        }
    }

    boolean flightNumberAlreadyExists(String flightNumber) {
        try {
            crud.findByFlightNumber(flightNumber);
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }
}
