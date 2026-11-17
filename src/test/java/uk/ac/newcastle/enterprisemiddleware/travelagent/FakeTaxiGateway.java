package uk.ac.newcastle.enterprisemiddleware.travelagent;

import com.fasterxml.jackson.databind.JsonNode;

import javax.enterprise.inject.Vetoed;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for the real Taxi service in tests, installed with QuarkusMock. Records what was booked and cancelled,
 * and can be told to fail, so every failure scenario can be tested without depending on a colleague's live service.
 */
@Vetoed
public class FakeTaxiGateway extends TaxiGateway {

    private final AtomicLong nextId = new AtomicLong(1000);
    public final List<Long> booked = new CopyOnWriteArrayList<>();
    public final List<Long> cancelled = new CopyOnWriteArrayList<>();
    public volatile RemoteServiceException failBookingWith;
    public volatile RemoteServiceException failCancelWith;

    @Override
    public long book(long taxiId, LocalDate date) {
        if (failBookingWith != null) {
            throw failBookingWith;
        }
        long id = nextId.incrementAndGet();
        booked.add(id);
        return id;
    }

    @Override
    public void cancel(long bookingId) {
        if (failCancelWith != null) {
            throw failCancelWith;
        }
        cancelled.add(bookingId);
    }

    @Override
    public List<JsonNode> listTaxis() {
        return List.of();
    }
}
