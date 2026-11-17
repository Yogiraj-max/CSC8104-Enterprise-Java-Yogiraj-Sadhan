package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.function.Supplier;

/**
 * Helpers for calling colleagues' services through the MicroProfile REST clients.
 * Every failure (an HTTP error status, or no response at all) is turned into a {@link RemoteServiceException}.
 */
final class RemoteCalls {

    private RemoteCalls() { }

    /** Runs a remote call; any failure becomes a RemoteServiceException naming the service and what it was doing. */
    static <T> T call(String service, String action, Supplier<T> remoteCall) {
        try {
            return remoteCall.get();
        } catch (WebApplicationException e) {
            Response response = e.getResponse();
            throw new RemoteServiceException(service, action, response.getStatus(), bodyOf(response));
        } catch (RemoteServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            // ProcessingException, timeouts, DNS errors, etc: the service did not answer.
            throw new RemoteServiceException(service, action, 0, e.getMessage());
        }
    }

    /** Cancels a remote booking. A 404 means it is already gone (e.g. their in-memory database was reset), so it counts as cancelled. */
    static void cancel(String service, long bookingId, Runnable remoteCall) {
        try {
            call(service, "cancel booking " + bookingId, () -> {
                remoteCall.run();
                return null;
            });
        } catch (RemoteServiceException e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }
    }

    /** Reads the "id" field of a record a remote service has just created. */
    static long idOf(String service, String what, JsonNode created) {
        if (created == null || !created.hasNonNull("id")) {
            throw new RemoteServiceException(service, "return the id of the new " + what, 0, "response had no id");
        }
        return created.get("id").asLong();
    }

    private static String bodyOf(Response response) {
        try {
            return response.hasEntity() ? response.readEntity(String.class) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
