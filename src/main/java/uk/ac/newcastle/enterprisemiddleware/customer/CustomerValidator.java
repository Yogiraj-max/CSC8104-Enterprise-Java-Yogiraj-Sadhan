package uk.ac.newcastle.enterprisemiddleware.customer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates a Customer: the Bean Validation annotations on the entity, plus the email uniqueness rule.
 */
@ApplicationScoped
public class CustomerValidator {

    @Inject
    Validator validator;

    @Inject
    CustomerRepository crud;

    void validateCustomer(Customer customer) throws ConstraintViolationException, UniqueEmailException {
        Set<ConstraintViolation<Customer>> violations = validator.validate(customer);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<ConstraintViolation<?>>(violations));
        }
        if (emailAlreadyExists(customer.getEmail())) {
            throw new UniqueEmailException("Unique Email Violation");
        }
    }

    boolean emailAlreadyExists(String email) {
        try {
            crud.findByEmail(email);
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }
}
