package uk.ac.newcastle.enterprisemiddleware.customer;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.logging.Logger;

/**
 * Business logic for Customers. Sits between the REST layer and the persistence layer.
 * Public so that GuestBookingRestService (a different package) can use it.
 */
@Dependent
public class CustomerService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    CustomerValidator validator;

    @Inject
    CustomerRepository crud;

    public List<Customer> findAllOrderedByName() {
        return crud.findAllOrderedByName();
    }

    public Customer findById(Long id) {
        return crud.findById(id);
    }

    public Customer create(Customer customer) {
        log.info("CustomerService.create() - Creating " + customer.getName());
        customer.setId(null); // the id is never settable by an API consumer
        validator.validateCustomer(customer);
        return crud.create(customer);
    }

    public Customer delete(Customer customer) {
        log.info("CustomerService.delete() - Deleting " + customer.getName());
        return crud.delete(customer);
    }
}
