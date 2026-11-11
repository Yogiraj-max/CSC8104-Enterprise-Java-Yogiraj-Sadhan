package uk.ac.newcastle.enterprisemiddleware.guestbooking;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import uk.ac.newcastle.enterprisemiddleware.booking.Booking;
import uk.ac.newcastle.enterprisemiddleware.customer.Customer;

import java.io.Serializable;

/**
 * Plain (non-persisted, NOT an @Entity) bean that lets Jackson deserialise a request body holding a new Customer
 * and a Booking together, e.g.
 *
 * {
 *   "customer": {"name": "Jane Doe", "email": "jane@example.com", "phoneNumber": "07123456789"},
 *   "booking":  {"flightId": 1, "bookingDate": "2027-01-15"}
 * }
 *
 * The booking's customer is left blank; GuestBookingRestService sets it to the newly created Customer.
 */
@Schema(description = "A new Customer plus a Booking for them, created together in one transaction")
public class GuestBooking implements Serializable {

    private static final long serialVersionUID = 1L;

    private Customer customer;

    private Booking booking;

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
}
