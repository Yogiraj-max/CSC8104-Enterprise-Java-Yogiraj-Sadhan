package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * REST client for the Hotel service (Sadit Indramangala). Base URL: quarkus.rest-client.hotel-api.url
 *
 * Their API: bookings are {"customerId", "hotelId", "bookingDate"}; customers are {"name", "email", "phoneNumber"};
 * hotels are {"name", "postcode", "phoneNumber"}.
 */
@Path("/")
@RegisterRestClient(configKey = "hotel-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface HotelApi {

    @GET
    @Path("/customers/email/{email}")
    JsonNode findCustomerByEmail(@PathParam("email") String email);

    @POST
    @Path("/customers")
    JsonNode createCustomer(Map<String, Object> customer);

    @GET
    @Path("/hotels")
    List<JsonNode> listHotels();

    @POST
    @Path("/hotels")
    JsonNode createHotel(JsonNode hotel);

    @POST
    @Path("/bookings")
    JsonNode createBooking(Map<String, Object> booking);

    @DELETE
    @Path("/bookings/{id}")
    void cancelBooking(@PathParam("id") long id);
}
