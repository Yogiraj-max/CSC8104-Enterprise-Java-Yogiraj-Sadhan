package uk.ac.newcastle.enterprisemiddleware.booking;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates a Booking: Bean Validation (future date, customer and flight present) and flight+date uniqueness.
 */
@ApplicationScoped
public class BookingValidator {

    @Inject
    Validator validator;

    @Inject
    BookingRepository crud;

    void validateBooking(Booking booking) throws ConstraintViolationException, UniqueFlightDateException {
        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<ConstraintViolation<?>>(violations));
        }
        if (!crud.findByFlightAndDate(booking.getFlight().getId(), booking.getBookingDate()).isEmpty()) {
            throw new UniqueFlightDateException("Flight " + booking.getFlight().getFlightNumber()
                    + " is already booked on " + booking.getBookingDate());
        }
    }
}
