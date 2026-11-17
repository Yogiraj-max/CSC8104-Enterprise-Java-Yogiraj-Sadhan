package uk.ac.newcastle.enterprisemiddleware.travelagent;

/**
 * Thrown when a colleague's service (Taxi or Hotel) refuses a request or cannot be reached.
 *
 * {@link #getStatus()} is the HTTP status the remote service returned, or 0 if there was no response at all
 * (service down, asleep, timed out or unreachable).
 */
public class RemoteServiceException extends RuntimeException {

    private final String service;
    private final int status;

    public RemoteServiceException(String service, String action, int status, String detail) {
        super(service + " service could not " + action
                + (status > 0 ? " (HTTP " + status + ")" : " (no response)")
                + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.service = service;
        this.status = status;
    }

    /** "Taxi" or "Hotel". */
    public String getService() { return service; }

    /** HTTP status from the remote service, or 0 if it could not be reached. */
    public int getStatus() { return status; }
}
