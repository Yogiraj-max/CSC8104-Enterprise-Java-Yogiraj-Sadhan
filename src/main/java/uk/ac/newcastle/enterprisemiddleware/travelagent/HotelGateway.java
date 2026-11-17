package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Everything the TravelAgent does with the Hotel service.
 *
 * All hotel bookings are made by ONE customer that represents this travel agent. It is found by email before each
 * booking and re-created if the Hotel service's in-memory database has been wiped.
 */
@ApplicationScoped
public class HotelGateway {

    static final String SERVICE = "Hotel";

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    @RestClient
    HotelApi api;

    @ConfigProperty(name = "travelagent.customer.name")
    String agentName;

    @ConfigProperty(name = "travelagent.customer.email")
    String agentEmail;

    @ConfigProperty(name = "travelagent.customer.phone")
    String agentPhone;

    public List<JsonNode> listHotels() {
        return RemoteCalls.call(SERVICE, "list hotels", () -> api.listHotels());
    }

    public JsonNode createHotel(JsonNode hotel) {
        return RemoteCalls.call(SERVICE, "create the hotel", () -> api.createHotel(hotel));
    }

    /** Books the hotel for the travel agent on the given date and returns the remote booking id. */
    public long book(long hotelId, LocalDate date) {
        long customerId = agentCustomerId();
        Map<String, Object> booking = new HashMap<>();
        booking.put("customerId", customerId);
        booking.put("hotelId", hotelId);
        booking.put("bookingDate", date.toString());
        JsonNode created = RemoteCalls.call(SERVICE, "book hotel " + hotelId + " on " + date, () -> api.createBooking(booking));
        long bookingId = RemoteCalls.idOf(SERVICE, "hotel booking", created);
        log.info("HotelGateway - booked hotel " + hotelId + " on " + date + ", remote booking id " + bookingId);
        return bookingId;
    }

    /** Cancels a remote hotel booking. Already-gone bookings (404) count as cancelled. */
    public void cancel(long bookingId) {
        RemoteCalls.cancel(SERVICE, bookingId, () -> api.cancelBooking(bookingId));
        log.info("HotelGateway - cancelled remote hotel booking " + bookingId);
    }

    /** Id of the travel agent's customer record in the Hotel service, creating it if needed. */
    long agentCustomerId() {
        try {
            JsonNode found = RemoteCalls.call(SERVICE, "find the travel agent customer", () -> api.findCustomerByEmail(agentEmail));
            return RemoteCalls.idOf(SERVICE, "customer", found);
        } catch (RemoteServiceException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", agentName);
        customer.put("email", agentEmail);
        customer.put("phoneNumber", agentPhone);
        JsonNode created = RemoteCalls.call(SERVICE, "create the travel agent customer", () -> api.createCustomer(customer));
        return RemoteCalls.idOf(SERVICE, "customer", created);
    }
}
