package uk.ac.newcastle.enterprisemiddleware.travelagent;

import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * A TravelAgent booking (or cancellation) that could not be completed. Carries the HTTP status to return and a
 * field -> message map, including what was done to undo the parts that had already been booked.
 */
public class TravelAgentBookingException extends RuntimeException {

    private final Response.Status status;
    private final Map<String, String> reasons;

    public TravelAgentBookingException(Response.Status status, String message, Map<String, String> reasons, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.reasons = reasons;
    }

    public Response.Status getStatus() { return status; }

    public Map<String, String> getReasons() { return reasons; }
}
