package uk.ac.newcastle.enterprisemiddleware.util;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a Bean Validation ConstraintViolationException into a field -> message map that is returned
 * to the client in the "reasons" part of the error JSON.
 */
public final class ViolationReasons {

    private ViolationReasons() { }

    public static Map<String, String> of(ConstraintViolationException ce) {
        Map<String, String> reasons = new HashMap<>();
        for (ConstraintViolation<?> violation : ce.getConstraintViolations()) {
            reasons.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return reasons;
    }

    public static Map<String, String> single(String field, String message) {
        Map<String, String> reasons = new HashMap<>();
        reasons.put(field, message);
        return reasons;
    }
}
