package uk.ac.newcastle.enterprisemiddleware.travelagent;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

/**
 * Persistence layer for TravelAgentBookings.
 */
@RequestScoped
public class TravelAgentBookingRepository {

    @Inject
    EntityManager em;

    List<TravelAgentBooking> findAll() {
        return em.createNamedQuery(TravelAgentBooking.FIND_ALL, TravelAgentBooking.class).getResultList();
    }

    List<TravelAgentBooking> findAllByCustomerId(Long customerId) {
        return em.createNamedQuery(TravelAgentBooking.FIND_BY_CUSTOMER, TravelAgentBooking.class)
                .setParameter("customerId", customerId)
                .getResultList();
    }

    TravelAgentBooking findById(Long id) {
        return em.find(TravelAgentBooking.class, id);
    }

    TravelAgentBooking create(TravelAgentBooking booking) {
        em.persist(booking);
        return booking;
    }
}
