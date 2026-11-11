package uk.ac.newcastle.enterprisemiddleware.flight;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.logging.Logger;

/**
 * Persistence layer for Flights.
 */
@RequestScoped
public class FlightRepository {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    EntityManager em;

    List<Flight> findAllOrderedByNumber() {
        return em.createNamedQuery(Flight.FIND_ALL, Flight.class).getResultList();
    }

    Flight findById(Long id) {
        return em.find(Flight.class, id);
    }

    /** @throws javax.persistence.NoResultException if no Flight has this number */
    Flight findByFlightNumber(String flightNumber) {
        return em.createNamedQuery(Flight.FIND_BY_NUMBER, Flight.class)
                .setParameter("flightNumber", flightNumber)
                .getSingleResult();
    }

    Flight create(Flight flight) {
        log.info("FlightRepository.create() - Creating " + flight.getFlightNumber());
        em.persist(flight);
        return flight;
    }

    Flight delete(Flight flight) {
        log.info("FlightRepository.delete() - Deleting " + flight.getFlightNumber());
        em.remove(em.contains(flight) ? flight : em.merge(flight));
        return flight;
    }
}
