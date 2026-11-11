package uk.ac.newcastle.enterprisemiddleware.booking;

import uk.ac.newcastle.enterprisemiddleware.customer.Customer;
import uk.ac.newcastle.enterprisemiddleware.customer.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.flight.Flight;
import uk.ac.newcastle.enterprisemiddleware.flight.FlightService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.logging.Logger;

/**
 * Business logic for Bookings. Resolves the customerId / flightId sent by the client into real entities,
 * validates the booking and persists it.
 */
@Dependent
public class BookingService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    BookingValidator validator;

    @Inject
    BookingRepository crud;

    @Inject
    CustomerService customerService;

    @Inject
    FlightService flightService;

    public List<Booking> findAll() {
        return crud.findAll();
    }

    public List<Booking> findAllByCustomerId(Long customerId) {
        return crud.findAllByCustomerId(customerId);
    }

    public Booking findById(Long id) {
        return crud.findById(id);
    }

    /**
     * Creates a Booking. If the Booking already holds a Customer object (as GuestBookingRestService does with a
     * customer it has just created) that Customer is used; otherwise the customerId from the JSON is looked up.
     */
    public Booking create(Booking booking) {
        log.info("BookingService.create() - Creating " + booking);
        booking.setId(null);

        if (booking.getCustomer() == null) {
            booking.setCustomer(resolveCustomer(booking.getCustomerId()));
        }
        booking.setFlight(resolveFlight(booking.getFlightId()));

        validator.validateBooking(booking);

        // Keep the in-memory relationship consistent on both sides.
        booking.getCustomer().getBookings().add(booking);
        booking.getFlight().getBookings().add(booking);

        return crud.create(booking);
    }

    public Booking delete(Booking booking) {
        log.info("BookingService.delete() - Cancelling " + booking);
        return crud.delete(booking);
    }

    private Customer resolveCustomer(Long customerId) {
        if (customerId == null) {
            throw new InvalidBookingReferenceException("customerId", "A customerId must be supplied");
        }
        Customer customer = customerService.findById(customerId);
        if (customer == null) {
            throw new InvalidBookingReferenceException("customerId", "No Customer with the id " + customerId + " exists");
        }
        return customer;
    }

    private Flight resolveFlight(Long flightId) {
        if (flightId == null) {
            throw new InvalidBookingReferenceException("flightId", "A flightId must be supplied");
        }
        Flight flight = flightService.findById(flightId);
        if (flight == null) {
            throw new InvalidBookingReferenceException("flightId", "No Flight with the id " + flightId + " exists");
        }
        return flight;
    }
}
