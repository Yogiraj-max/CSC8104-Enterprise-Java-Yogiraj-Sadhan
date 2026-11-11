package uk.ac.newcastle.enterprisemiddleware.flight;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.logging.Logger;

/**
 * Business logic for Flights.
 */
@Dependent
public class FlightService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    FlightValidator validator;

    @Inject
    FlightRepository crud;

    public List<Flight> findAllOrderedByNumber() {
        return crud.findAllOrderedByNumber();
    }

    public Flight findById(Long id) {
        return crud.findById(id);
    }

    public Flight create(Flight flight) {
        log.info("FlightService.create() - Creating " + flight.getFlightNumber());
        flight.setId(null);
        validator.validateFlight(flight);
        return crud.create(flight);
    }

    public Flight delete(Flight flight) {
        log.info("FlightService.delete() - Deleting " + flight.getFlightNumber());
        return crud.delete(flight);
    }
}
