package uk.ac.newcastle.enterprisemiddleware.booking;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * Persistence layer for Bookings.
 */
@RequestScoped
public class BookingRepository {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    EntityManager em;

    List<Booking> findAll() {
        return em.createNamedQuery(Booking.FIND_ALL, Booking.class).getResultList();
    }

    List<Booking> findAllByCustomerId(Long customerId) {
        return em.createNamedQuery(Booking.FIND_BY_CUSTOMER, Booking.class)
                .setParameter("customerId", customerId)
                .getResultList();
    }

    List<Booking> findByFlightAndDate(Long flightId, LocalDate bookingDate) {
        return em.createNamedQuery(Booking.FIND_BY_FLIGHT_AND_DATE, Booking.class)
                .setParameter("flightId", flightId)
                .setParameter("bookingDate", bookingDate)
                .getResultList();
    }

    Booking findById(Long id) {
        return em.find(Booking.class, id);
    }

    Booking create(Booking booking) {
        log.info("BookingRepository.create() - Creating " + booking);
        em.persist(booking);
        return booking;
    }

    Booking delete(Booking booking) {
        log.info("BookingRepository.delete() - Deleting " + booking);
        Booking managed = em.contains(booking) ? booking : em.merge(booking);
        // Keep both sides of the relationship in sync so Hibernate does not try to keep the booking alive.
        if (managed.getCustomer() != null) managed.getCustomer().getBookings().remove(managed);
        if (managed.getFlight() != null) managed.getFlight().getBookings().remove(managed);
        em.remove(managed);
        return booking;
    }
}
