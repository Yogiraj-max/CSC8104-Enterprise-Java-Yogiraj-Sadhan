package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * REST client for the Taxi service (Parth Bhawar). Base URL: quarkus.rest-client.taxi-api.url
 *
 * Their API: bookings are {"customerId", "taxiId", "date"}; customers are {"name", "email", "phoneNumber"}.
 */
@Path("/")
@RegisterRestClient(configKey = "taxi-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TaxiApi {

    @GET
    @Path("/customers")
    List<JsonNode> listCustomers();

    @POST
    @Path("/customers")
    JsonNode createCustomer(Map<String, Object> customer);

    @GET
    @Path("/taxis")
    List<JsonNode> listTaxis();

    @POST
    @Path("/taxis")
    JsonNode createTaxi(JsonNode taxi);

    @POST
    @Path("/bookings")
    JsonNode createBooking(Map<String, Object> booking);

    @DELETE
    @Path("/bookings/{id}")
    void cancelBooking(@PathParam("id") long id);
}
