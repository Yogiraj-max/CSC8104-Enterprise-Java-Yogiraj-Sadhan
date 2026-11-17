package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import uk.ac.newcastle.enterprisemiddleware.customer.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TravelAgent resource (Part 3): books a flight (this service), a taxi (Parth Bhawar's Taxi service) and a hotel
 * (Sadit Indramangala's Hotel service) together, so a customer never ends up with only part of a trip.
 */
@Path("/travelagent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Travel Agent", description = "Book a flight, a taxi and a hotel together - all or nothing")
public class TravelAgentRestService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    TravelAgentService service;

    @Inject
    CustomerService customerService;

    @Inject
    TaxiGateway taxis;

    @Inject
    HotelGateway hotels;

    // ---------------------------------------------- Aggregate bookings ----------------------------------------------

    @GET
    @Path("/bookings")
    @Operation(summary = "List TravelAgent bookings",
            description = "Returns every TravelAgent booking, or only one Customer's when ?customerId= is given.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of TravelAgent bookings (may be empty)"),
            @APIResponse(responseCode = "404", description = "customerId was given but no Customer with that id exists")
    })
    public Response retrieveTravelAgentBookings(
            @Parameter(description = "Optional: only return bookings for this Customer")
            @QueryParam("customerId") Long customerId) {
        if (customerId == null) {
            return Response.ok(service.findAll()).build();
        }
        if (customerService.findById(customerId) == null) {
            throw new RestServiceException("No Customer with the id " + customerId + " was found!", Response.Status.NOT_FOUND);
        }
        return Response.ok(service.findAllByCustomerId(customerId)).build();
    }

    @GET
    @Path("/bookings/{id:[0-9]+}")
    @Operation(summary = "Fetch a TravelAgent booking by id")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "TravelAgent booking found"),
            @APIResponse(responseCode = "404", description = "No TravelAgent booking with this id exists")
    })
    public Response retrieveTravelAgentBookingById(
            @Parameter(description = "Id of the TravelAgent booking", required = true)
            @Schema(minimum = "0")
            @PathParam("id") long id) {
        return Response.ok(findOr404(id)).build();
    }

    @POST
    @Path("/bookings")
    @Operation(summary = "Book a flight, taxi and hotel together",
            description = "Books, in order: (1) the taxi in the Taxi service, (2) the hotel in the Hotel service, "
                    + "(3) the flight in this service. If any step fails, the steps that already succeeded are cancelled, "
                    + "so the customer never has a partial booking. The 'compensation' reason says what was undone. "
                    + "customerId and flightId are ids in this service; taxiId and hotelId are ids in the colleagues' services "
                    + "(see GET /travelagent/taxis and GET /travelagent/hotels). "
                    + "Example: {\"customerId\":1,\"flightId\":1,\"taxiId\":1,\"hotelId\":1,\"bookingDate\":\"2027-01-15\"}")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Flight, taxi and hotel all booked; the body is the TravelAgent booking"),
            @APIResponse(responseCode = "400", description = "A field is missing or invalid (unknown customerId or flightId, date missing, "
                    + "malformed or not in the future), or a colleague's service rejected the taxiId/hotelId. Nothing is kept booked."),
            @APIResponse(responseCode = "409", description = "The flight, taxi or hotel is already booked on that date. "
                    + "Any parts already booked were cancelled."),
            @APIResponse(responseCode = "502", description = "The Taxi or Hotel service could not be reached or failed. "
                    + "Any parts already booked were cancelled."),
            @APIResponse(responseCode = "500", description = "Unexpected error. Any parts already booked were cancelled.")
    })
    public Response createTravelAgentBooking(
            @Parameter(description = "customerId, flightId, taxiId, hotelId and bookingDate", required = true)
            TravelAgentBookingRequest request) {
        try {
            TravelAgentBooking booking = service.create(request);
            return Response.status(Response.Status.CREATED).entity(booking).build();
        } catch (TravelAgentBookingException e) {
            throw new RestServiceException(e.getMessage(), e.getReasons(), e.getStatus(), e);
        }
    }

    @DELETE
    @Path("/bookings/{id:[0-9]+}")
    @Operation(summary = "Cancel a TravelAgent booking",
            description = "Cancels the taxi and hotel bookings in the colleagues' services, then the flight booking here.")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Taxi, hotel and flight bookings all cancelled"),
            @APIResponse(responseCode = "404", description = "No TravelAgent booking with this id exists"),
            @APIResponse(responseCode = "502", description = "The Taxi or Hotel service could not cancel its part. "
                    + "The TravelAgent booking is kept so the cancel can be tried again.")
    })
    public Response cancelTravelAgentBooking(
            @Parameter(description = "Id of the TravelAgent booking to cancel", required = true)
            @Schema(minimum = "0")
            @PathParam("id") long id) {
        TravelAgentBooking booking = findOr404(id);
        try {
            service.cancel(booking);
        } catch (TravelAgentBookingException e) {
            throw new RestServiceException(e.getMessage(), e.getReasons(), e.getStatus(), e);
        }
        return Response.noContent().build();
    }

    // ------------------------------- Helpers to see / add taxis and hotels remotely -------------------------------

    @GET
    @Path("/taxis")
    @Operation(summary = "List taxis in the Taxi service", description = "Shows the taxi ids you can use as taxiId.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Taxis from the Taxi service"),
            @APIResponse(responseCode = "502", description = "The Taxi service could not be reached")
    })
    public Response listRemoteTaxis() {
        return remote(() -> taxis.listTaxis(), Response.Status.OK);
    }

    @POST
    @Path("/taxis")
    @Operation(summary = "Create a taxi in the Taxi service",
            description = "Forwards the body to the Taxi service. Example: {\"registration\":\"AB12CDE\",\"seats\":4}")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Taxi created in the Taxi service"),
            @APIResponse(responseCode = "400", description = "The Taxi service rejected the taxi"),
            @APIResponse(responseCode = "409", description = "The Taxi service already has this registration"),
            @APIResponse(responseCode = "502", description = "The Taxi service could not be reached")
    })
    public Response createRemoteTaxi(JsonNode taxi) {
        return remote(() -> taxis.createTaxi(taxi), Response.Status.CREATED);
    }

    @GET
    @Path("/hotels")
    @Operation(summary = "List hotels in the Hotel service", description = "Shows the hotel ids you can use as hotelId.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Hotels from the Hotel service"),
            @APIResponse(responseCode = "502", description = "The Hotel service could not be reached")
    })
    public Response listRemoteHotels() {
        return remote(() -> hotels.listHotels(), Response.Status.OK);
    }

    @POST
    @Path("/hotels")
    @Operation(summary = "Create a hotel in the Hotel service",
            description = "Forwards the body to the Hotel service. Example: {\"name\":\"Grand Hotel\",\"postcode\":\"NE17RU\",\"phoneNumber\":\"01912345678\"}")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Hotel created in the Hotel service"),
            @APIResponse(responseCode = "400", description = "The Hotel service rejected the hotel"),
            @APIResponse(responseCode = "409", description = "The Hotel service already has a hotel with these details"),
            @APIResponse(responseCode = "502", description = "The Hotel service could not be reached")
    })
    public Response createRemoteHotel(JsonNode hotel) {
        return remote(() -> hotels.createHotel(hotel), Response.Status.CREATED);
    }

    // ----------------------------------------------------------------------------------------------------------------

    private TravelAgentBooking findOr404(long id) {
        TravelAgentBooking booking = service.findById(id);
        if (booking == null) {
            throw new RestServiceException("No TravelAgent booking with the id " + id + " was found!", Response.Status.NOT_FOUND);
        }
        return booking;
    }

    private Response remote(java.util.function.Supplier<Object> call, Response.Status success) {
        try {
            return Response.status(success).entity(call.get()).build();
        } catch (RemoteServiceException e) {
            Response.Status status = e.getStatus() == 400 ? Response.Status.BAD_REQUEST
                    : e.getStatus() == 409 ? Response.Status.CONFLICT
                    : Response.Status.BAD_GATEWAY;
            Map<String, String> reasons = new HashMap<>();
            reasons.put(e.getService().toLowerCase(), e.getMessage());
            throw new RestServiceException(e.getService() + " service request failed", reasons, status, e);
        }
    }
}
