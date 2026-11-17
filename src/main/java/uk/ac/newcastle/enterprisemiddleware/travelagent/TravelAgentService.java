package uk.ac.newcastle.enterprisemiddleware.travelagent;

import uk.ac.newcastle.enterprisemiddleware.booking.Booking;
import uk.ac.newcastle.enterprisemiddleware.booking.BookingService;
import uk.ac.newcastle.enterprisemiddleware.booking.InvalidBookingReferenceException;
import uk.ac.newcastle.enterprisemiddleware.booking.UniqueFlightDateException;
import uk.ac.newcastle.enterprisemiddleware.customer.CustomerService;
import uk.ac.newcastle.enterprisemiddleware.flight.FlightService;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Status;
import javax.transaction.UserTransaction;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Business logic of the TravelAgent (Part 3).
 *
 * A TravelAgentBooking books three things on the same date, in this order:
 *   1. a taxi   - in the Taxi service (remote)
 *   2. a hotel  - in the Hotel service (remote)
 *   3. a flight - in this service (local), saved together with the TravelAgentBooking in one JTA transaction
 *
 * The remote services cannot take part in our database transaction, so if any step fails, the steps that already
 * succeeded are undone by calling each service's cancel operation (a "compensating action"). This way the customer
 * never ends up with a partial booking, e.g. a taxi and hotel but no flight.
 */
@Dependent
public class TravelAgentService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    UserTransaction transaction;

    @Inject
    TaxiGateway taxis;

    @Inject
    HotelGateway hotels;

    @Inject
    CustomerService customerService;

    @Inject
    FlightService flightService;

    @Inject
    BookingService bookingService;

    @Inject
    TravelAgentBookingRepository crud;

    public List<TravelAgentBooking> findAll() {
        return crud.findAll();
    }

    public List<TravelAgentBooking> findAllByCustomerId(Long customerId) {
        return crud.findAllByCustomerId(customerId);
    }

    public TravelAgentBooking findById(Long id) {
        return crud.findById(id);
    }

    /**
     * Books taxi, hotel and flight. Either all three are booked, or none are.
     *
     * @throws TravelAgentBookingException if the request is invalid or any part fails (parts already booked are cancelled)
     */
    public TravelAgentBooking create(TravelAgentBookingRequest request) {
        validate(request);
        LocalDate date = request.getBookingDate();

        Long taxiBookingId = null;
        Long hotelBookingId = null;
        try {
            taxiBookingId = taxis.book(request.getTaxiId(), date);
            hotelBookingId = hotels.book(request.getHotelId(), date);
            TravelAgentBooking saved = saveLocally(request, taxiBookingId, hotelBookingId);
            log.info("TravelAgentService.create() - booked " + saved);
            return saved;
        } catch (RuntimeException failure) {
            log.warning("TravelAgentService.create() - booking failed, undoing earlier parts: " + failure.getMessage());
            List<String> undone = compensate(taxiBookingId, hotelBookingId);
            throw failureFor(failure, undone);
        }
    }

    /**
     * Cancels the taxi and hotel bookings in the remote services, then deletes the local flight booking and this
     * TravelAgentBooking. If a remote cancel fails, nothing is deleted locally so the cancel can be retried.
     */
    public void cancel(TravelAgentBooking booking) {
        Map<String, String> problems = new LinkedHashMap<>();
        try {
            taxis.cancel(booking.getTaxiBookingId());
        } catch (RemoteServiceException e) {
            problems.put("taxi", e.getMessage());
        }
        try {
            hotels.cancel(booking.getHotelBookingId());
        } catch (RemoteServiceException e) {
            problems.put("hotel", e.getMessage());
        }
        if (!problems.isEmpty()) {
            throw new TravelAgentBookingException(Response.Status.BAD_GATEWAY,
                    "Could not cancel every part of the booking. It has been kept so the cancel can be tried again.",
                    problems, null);
        }
        deleteLocally(booking);
        log.info("TravelAgentService.cancel() - cancelled " + booking);
    }

    // ------------------------------------------------------------------------------------------------------------

    /** Checks everything we can check locally BEFORE contacting any remote service. */
    private void validate(TravelAgentBookingRequest request) {
        Map<String, String> reasons = new LinkedHashMap<>();
        if (request == null) {
            reasons.put("body", "A request body with customerId, flightId, taxiId, hotelId and bookingDate is required");
        } else {
            if (request.getCustomerId() == null) {
                reasons.put("customerId", "A customerId must be supplied");
            } else if (customerService.findById(request.getCustomerId()) == null) {
                reasons.put("customerId", "No Customer with the id " + request.getCustomerId() + " exists");
            }
            if (request.getFlightId() == null) {
                reasons.put("flightId", "A flightId must be supplied");
            } else if (flightService.findById(request.getFlightId()) == null) {
                reasons.put("flightId", "No Flight with the id " + request.getFlightId() + " exists");
            }
            if (request.getTaxiId() == null) {
                reasons.put("taxiId", "A taxiId (from the Taxi service) must be supplied");
            }
            if (request.getHotelId() == null) {
                reasons.put("hotelId", "A hotelId (from the Hotel service) must be supplied");
            }
            if (request.getBookingDate() == null) {
                reasons.put("bookingDate", "A booking date must be supplied");
            } else if (!request.getBookingDate().isAfter(LocalDate.now())) {
                reasons.put("bookingDate", "The booking date must be in the future");
            }
        }
        if (!reasons.isEmpty()) {
            throw new TravelAgentBookingException(Response.Status.BAD_REQUEST, "Invalid travel agent booking", reasons, null);
        }
    }

    /** Step 3: the flight booking and the TravelAgentBooking are saved in ONE JTA transaction (manually demarcated). */
    private TravelAgentBooking saveLocally(TravelAgentBookingRequest request, long taxiBookingId, long hotelBookingId) {
        try {
            transaction.begin();

            Booking flightBooking = new Booking();
            flightBooking.setCustomerId(request.getCustomerId());
            flightBooking.setFlightId(request.getFlightId());
            flightBooking.setBookingDate(request.getBookingDate());
            bookingService.create(flightBooking);

            TravelAgentBooking travelAgentBooking = new TravelAgentBooking();
            travelAgentBooking.setFlightBooking(flightBooking);
            travelAgentBooking.setTaxiBookingId(taxiBookingId);
            travelAgentBooking.setHotelBookingId(hotelBookingId);
            travelAgentBooking.setTaxiId(request.getTaxiId());
            travelAgentBooking.setHotelId(request.getHotelId());
            flightBooking.setTravelAgentBooking(travelAgentBooking);
            crud.create(travelAgentBooking);

            transaction.commit();
            return travelAgentBooking;
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new IllegalStateException("Could not save the flight booking: " + e.getMessage(), e);
        }
    }

    private void deleteLocally(TravelAgentBooking booking) {
        try {
            transaction.begin();
            // Deleting the flight booking cascades (CascadeType.REMOVE on Booking) to the TravelAgentBooking.
            bookingService.delete(booking.getFlightBooking());
            transaction.commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } catch (Exception e) {
            rollback();
            throw new IllegalStateException("Could not delete the local booking: " + e.getMessage(), e);
        }
    }

    private void rollback() {
        try {
            if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                transaction.rollback();
            }
        } catch (Exception e) {
            log.severe("TravelAgentService - rollback failed: " + e);
        }
    }

    /** Undoes the remote bookings that succeeded before the failure (newest first). Returns what happened. */
    private List<String> compensate(Long taxiBookingId, Long hotelBookingId) {
        List<String> undone = new ArrayList<>();
        if (hotelBookingId != null) {
            try {
                hotels.cancel(hotelBookingId);
                undone.add("hotel booking " + hotelBookingId + " cancelled");
            } catch (RuntimeException e) {
                log.severe("Could not cancel hotel booking " + hotelBookingId + ": " + e.getMessage());
                undone.add("WARNING: hotel booking " + hotelBookingId + " could NOT be cancelled: " + e.getMessage());
            }
        }
        if (taxiBookingId != null) {
            try {
                taxis.cancel(taxiBookingId);
                undone.add("taxi booking " + taxiBookingId + " cancelled");
            } catch (RuntimeException e) {
                log.severe("Could not cancel taxi booking " + taxiBookingId + ": " + e.getMessage());
                undone.add("WARNING: taxi booking " + taxiBookingId + " could NOT be cancelled: " + e.getMessage());
            }
        }
        return undone;
    }

    /** Turns whatever went wrong into the HTTP status and reasons to return to the client. */
    private TravelAgentBookingException failureFor(RuntimeException failure, List<String> undone) {
        Map<String, String> reasons = new LinkedHashMap<>();
        Response.Status status;

        if (failure instanceof RemoteServiceException) {
            RemoteServiceException remote = (RemoteServiceException) failure;
            reasons.put(remote.getService().toLowerCase(), remote.getMessage());
            if (remote.getStatus() == 409) {
                status = Response.Status.CONFLICT;
            } else if (remote.getStatus() == 400 || remote.getStatus() == 404) {
                status = Response.Status.BAD_REQUEST;
            } else {
                status = Response.Status.BAD_GATEWAY;
            }
        } else if (failure instanceof UniqueFlightDateException) {
            reasons.put("flight", failure.getMessage());
            status = Response.Status.CONFLICT;
        } else if (failure instanceof InvalidBookingReferenceException) {
            reasons.put(((InvalidBookingReferenceException) failure).getField(), failure.getMessage());
            status = Response.Status.BAD_REQUEST;
        } else if (failure instanceof ConstraintViolationException) {
            for (ConstraintViolation<?> v : ((ConstraintViolationException) failure).getConstraintViolations()) {
                reasons.put("flight." + v.getPropertyPath(), v.getMessage());
            }
            status = Response.Status.BAD_REQUEST;
        } else {
            reasons.put("error", String.valueOf(failure.getMessage()));
            status = Response.Status.INTERNAL_SERVER_ERROR;
        }

        if (!undone.isEmpty()) {
            reasons.put("compensation", String.join("; ", undone));
        }
        return new TravelAgentBookingException(status,
                "Travel agent booking failed, so no part of it was kept", reasons, failure);
    }
}
