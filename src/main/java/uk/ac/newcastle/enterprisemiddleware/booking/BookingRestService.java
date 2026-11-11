package uk.ac.newcastle.enterprisemiddleware.booking;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import uk.ac.newcastle.enterprisemiddleware.customer.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;
import uk.ac.newcastle.enterprisemiddleware.util.ViolationReasons;

import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

/**
 * REST endpoints for Bookings: create, list (optionally for one customer), get by id and cancel.
 */
@Path("/bookings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Create, list and cancel flight bookings")
public class BookingRestService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    BookingService service;

    @Inject
    CustomerService customerService;

    @GET
    @Operation(summary = "List Bookings",
            description = "Returns every Booking, or only the Bookings made by one Customer when ?customerId= is given.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of Bookings (may be empty)"),
            @APIResponse(responseCode = "404", description = "customerId was given but no Customer with that id exists")
    })
    public Response retrieveBookings(
            @Parameter(description = "Optional: only return Bookings made by this Customer")
            @QueryParam("customerId") Long customerId) {

        List<Booking> bookings;
        if (customerId == null) {
            bookings = service.findAll();
        } else {
            if (customerService.findById(customerId) == null) {
                throw new RestServiceException("No Customer with the id " + customerId + " was found!", Response.Status.NOT_FOUND);
            }
            bookings = service.findAllByCustomerId(customerId);
        }
        return Response.ok(bookings).build();
    }

    @GET
    @Path("/{id:[0-9]+}")
    @Operation(summary = "Fetch a Booking by id", description = "Returns the Booking with the given id.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Booking found"),
            @APIResponse(responseCode = "404", description = "No Booking with this id exists")
    })
    public Response retrieveBookingById(
            @Parameter(description = "Id of the Booking to fetch", required = true)
            @Schema(minimum = "0")
            @PathParam("id") long id) {
        Booking booking = service.findById(id);
        if (booking == null) {
            throw new RestServiceException("No Booking with the id " + id + " was found!", Response.Status.NOT_FOUND);
        }
        return Response.ok(booking).build();
    }

    @POST
    @Operation(summary = "Create a Booking",
            description = "Books a Flight for a Customer on a future date. "
                    + "Example body: {\"customerId\":1,\"flightId\":1,\"bookingDate\":\"2027-01-15\"}. "
                    + "Only one Booking is allowed per flight per date.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Booking created; the body contains the new Booking including its id"),
            @APIResponse(responseCode = "400", description = "Body missing, customerId/flightId missing or not an existing Customer/Flight, "
                    + "or bookingDate missing, malformed (expected yyyy-MM-dd) or not in the future"),
            @APIResponse(responseCode = "409", description = "That flight is already booked on that date"),
            @APIResponse(responseCode = "500", description = "Unexpected error whilst processing the request")
    })
    @Transactional
    public Response createBooking(
            @Parameter(description = "JSON with customerId, flightId and bookingDate", required = true)
            Booking booking) {

        if (booking == null) {
            throw new RestServiceException("Bad Request", Response.Status.BAD_REQUEST);
        }
        try {
            service.create(booking);
        } catch (ConstraintViolationException ce) {
            throw new RestServiceException("Bad Request", ViolationReasons.of(ce), Response.Status.BAD_REQUEST, ce);
        } catch (InvalidBookingReferenceException e) {
            throw new RestServiceException("Bad Request", ViolationReasons.single(e.getField(), e.getMessage()),
                    Response.Status.BAD_REQUEST, e);
        } catch (UniqueFlightDateException e) {
            throw new RestServiceException("Conflict", ViolationReasons.single("bookingDate", e.getMessage()),
                    Response.Status.CONFLICT, e);
        } catch (Exception e) {
            throw new RestServiceException(e);
        }
        log.info("createBooking completed. Booking = " + booking);
        return Response.status(Response.Status.CREATED).entity(booking).build();
    }

    @DELETE
    @Path("/{id:[0-9]+}")
    @Operation(summary = "Cancel a Booking", description = "Deletes the Booking with the given id.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Booking cancelled"),
            @APIResponse(responseCode = "404", description = "No Booking with this id exists"),
            @APIResponse(responseCode = "500", description = "Unexpected error whilst processing the request")
    })
    @Transactional
    public Response cancelBooking(
            @Parameter(description = "Id of the Booking to cancel", required = true)
            @Schema(minimum = "0")
            @PathParam("id") long id) {
        Booking booking = service.findById(id);
        if (booking == null) {
            throw new RestServiceException("No Booking with the id " + id + " was found!", Response.Status.NOT_FOUND);
        }
        try {
            service.delete(booking);
        } catch (Exception e) {
            throw new RestServiceException(e);
        }
        log.info("cancelBooking completed. Booking = " + booking);
        return Response.noContent().build();
    }
}
