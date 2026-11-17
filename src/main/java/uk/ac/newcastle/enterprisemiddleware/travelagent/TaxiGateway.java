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
 * Everything the TravelAgent does with the Taxi service.
 *
 * All taxi bookings are made by ONE customer that represents this travel agent (as the coursework suggests).
 * Their database is in memory and is wiped whenever their service restarts, so that customer is looked up by
 * email before each booking and re-created if it has gone.
 */
@ApplicationScoped
public class TaxiGateway {

    static final String SERVICE = "Taxi";

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    @RestClient
    TaxiApi api;

    @ConfigProperty(name = "travelagent.customer.name")
    String agentName;

    @ConfigProperty(name = "travelagent.customer.email")
    String agentEmail;

    @ConfigProperty(name = "travelagent.customer.phone")
    String agentPhone;

    public List<JsonNode> listTaxis() {
        return RemoteCalls.call(SERVICE, "list taxis", () -> api.listTaxis());
    }

    public JsonNode createTaxi(JsonNode taxi) {
        return RemoteCalls.call(SERVICE, "create the taxi", () -> api.createTaxi(taxi));
    }

    /** Books the taxi for the travel agent on the given date and returns the remote booking id. */
    public long book(long taxiId, LocalDate date) {
        long customerId = agentCustomerId();
        Map<String, Object> booking = new HashMap<>();
        booking.put("customerId", customerId);
        booking.put("taxiId", taxiId);
        booking.put("date", date.toString());
        JsonNode created = RemoteCalls.call(SERVICE, "book taxi " + taxiId + " on " + date, () -> api.createBooking(booking));
        long bookingId = RemoteCalls.idOf(SERVICE, "taxi booking", created);
        log.info("TaxiGateway - booked taxi " + taxiId + " on " + date + ", remote booking id " + bookingId);
        return bookingId;
    }

    /** Cancels a remote taxi booking. Already-gone bookings (404) count as cancelled. */
    public void cancel(long bookingId) {
        RemoteCalls.cancel(SERVICE, bookingId, () -> api.cancelBooking(bookingId));
        log.info("TaxiGateway - cancelled remote taxi booking " + bookingId);
    }

    /** Id of the travel agent's customer record in the Taxi service, creating it if needed. */
    long agentCustomerId() {
        Long existing = findAgent();
        if (existing != null) {
            return existing;
        }
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", agentName);
        customer.put("email", agentEmail);
        customer.put("phoneNumber", agentPhone);
        try {
            JsonNode created = RemoteCalls.call(SERVICE, "create the travel agent customer", () -> api.createCustomer(customer));
            return RemoteCalls.idOf(SERVICE, "customer", created);
        } catch (RemoteServiceException e) {
            // 409: someone created it between our lookup and our create - look it up again.
            Long raced = e.getStatus() == 409 ? findAgent() : null;
            if (raced == null) {
                throw e;
            }
            return raced;
        }
    }

    private Long findAgent() {
        List<JsonNode> customers = RemoteCalls.call(SERVICE, "list customers", () -> api.listCustomers());
        for (JsonNode c : customers) {
            if (c.hasNonNull("email") && agentEmail.equalsIgnoreCase(c.get("email").asText()) && c.hasNonNull("id")) {
                return c.get("id").asLong();
            }
        }
        return null;
    }
}
