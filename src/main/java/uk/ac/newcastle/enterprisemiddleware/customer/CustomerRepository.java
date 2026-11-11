package uk.ac.newcastle.enterprisemiddleware.customer;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.logging.Logger;

/**
 * Persistence layer for Customers. The only class that talks to the EntityManager for Customer data.
 */
@RequestScoped
public class CustomerRepository {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    EntityManager em;

    List<Customer> findAllOrderedByName() {
        return em.createNamedQuery(Customer.FIND_ALL, Customer.class).getResultList();
    }

    Customer findById(Long id) {
        return em.find(Customer.class, id);
    }

    /** @throws javax.persistence.NoResultException if no Customer has this email */
    Customer findByEmail(String email) {
        return em.createNamedQuery(Customer.FIND_BY_EMAIL, Customer.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    Customer create(Customer customer) {
        log.info("CustomerRepository.create() - Creating " + customer.getName());
        em.persist(customer);
        return customer;
    }

    Customer delete(Customer customer) {
        log.info("CustomerRepository.delete() - Deleting " + customer.getName());
        // em.merge makes sure we remove a managed instance. Hibernate then cascades the remove to the Bookings.
        em.remove(em.contains(customer) ? customer : em.merge(customer));
        return customer;
    }
}
