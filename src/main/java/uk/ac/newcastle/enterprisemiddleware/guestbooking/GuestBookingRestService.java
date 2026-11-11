package uk.ac.newcastle.enterprisemiddleware.guestbooking;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import uk.ac.newcastle.enterprisemiddleware.booking.Booking;
import uk.ac.newcastle.enterprisemiddleware.booking.BookingService;
import uk.ac.newcastle.enterprisemiddleware.booking.InvalidBookingReferenceException;
import uk.ac.newcastle.enterprisemiddleware.booking.UniqueFlightDateException;
import uk.ac.newcastle.enterprisemiddleware.customer.Customer;
import uk.ac.newcastle.enterprisemiddleware.customer.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.customer.UniqueEmailException;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Creates a brand new Customer and a Booking for them as ONE unit of work.
 *
 * The transaction is demarcated MANUALLY with the JTA UserTransaction API (begin / commit / rollback),
 * as required by the coursework - this method is deliberately NOT annotated with @Transactional.
 * If either the Customer or the Booking cannot be persisted the whole transaction is rolled back,
 * so we never end up with a Customer who has no Booking.
 */
@Path("/guestbookings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Guest Bookings", description = "Create a new Customer and a Booking in a single transaction")
public class GuestBookingRestService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    UserTransaction transaction;

    @Inject
    CustomerService customerService;

    @Inject
    BookingService bookingService;

    @POST
    @Operation(summary = "Create a Customer and a Booking together",
            description = "Creates the Customer, then a Booking for them, inside one JTA UserTransaction. "
                    + "If anything fails, nothing is saved. Example body: "
                    + "{\"customer\":{\"name\":\"Jane Doe\",\"email\":\"jane@example.com\",\"phoneNumber\":\"07123456789\"},"
                    + "\"booking\":{\"flightId\":1,\"bookingDate\":\"2027-01-15\"}}")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Customer and Booking created; the body contains the Booking"),
            @APIResponse(responseCode = "400", description = "Body incomplete, invalid Customer fields, unknown flightId, "
                    + "or bookingDate missing / not in the future. Transaction rolled back."),
            @APIResponse(responseCode = "409", description = "Customer email already used, or flight already booked on that date. "
                    + "Transaction rolled back."),
            @APIResponse(responseCode = "500", description = "Unexpected error; transaction rolled back")
    })
    public Response createGuestBooking(
            @Parameter(description = "JSON with a 'customer' object and a 'booking' object", required = true)
            GuestBooking guestBooking) {

        if (guestBooking == null || guestBooking.getCustomer() == null || guestBooking.getBooking() == null) {
            throw new RestServiceException("Bad Request: both 'customer' and 'booking' must be supplied",
                    Response.Status.BAD_REQUEST);
        }

        Customer customer = guestBooking.getCustomer();
        Booking booking = guestBooking.getBooking();
        String stage = "customer"; // which part we are persisting - used to label validation errors

        try {
            transaction.begin();

            customerService.create(customer);

            stage = "booking";
            booking.setCustomer(customer); // link the booking to the customer we just created
            bookingService.create(booking);

            transaction.commit();

        } catch (ConstraintViolationException ce) {
            rollback();
            Map<String, String> reasons = new HashMap<>();
            for (ConstraintViolation<?> v : ce.getConstraintViolations()) {
                reasons.put(stage + "." + v.getPropertyPath(), v.getMessage());
            }
            throw new RestServiceException("Bad Request - guest booking rolled back", reasons, Response.Status.BAD_REQUEST, ce);
        } catch (InvalidBookingReferenceException e) {
            rollback();
            throw new RestServiceException("Bad Request - guest booking rolled back",
                    single("booking." + e.getField(), e.getMessage()), Response.Status.BAD_REQUEST, e);
        } catch (UniqueEmailException e) {
            rollback();
            throw new RestServiceException("Conflict - guest booking rolled back",
                    single("customer.email", "That email is already used, please use a unique email"), Response.Status.CONFLICT, e);
        } catch (UniqueFlightDateException e) {
            rollback();
            throw new RestServiceException("Conflict - guest booking rolled back",
                    single("booking.bookingDate", e.getMessage()), Response.Status.CONFLICT, e);
        } catch (Exception e) {
            rollback();
            throw new RestServiceException("Guest booking failed and was rolled back", e);
        }

        log.info("createGuestBooking completed. Booking = " + booking);
        return Response.status(Response.Status.CREATED).entity(booking).build();
    }

    /** Roll back the current transaction if one is still active. */
    private void rollback() {
        try {
            if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                transaction.rollback();
                log.info("GuestBooking transaction rolled back");
            }
        } catch (SystemException | IllegalStateException | SecurityException e) {
            log.severe("Failed to roll back GuestBooking transaction: " + e);
        }
    }

    private static Map<String, String> single(String field, String message) {
        Map<String, String> reasons = new HashMap<>();
        reasons.put(field, message);
        return reasons;
    }
}
