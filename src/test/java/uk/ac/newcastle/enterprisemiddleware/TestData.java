package uk.ac.newcastle.enterprisemiddleware;

import io.restassured.http.ContentType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Small helpers shared by the integration tests. Request bodies are built as Maps so that dates are sent as
 * plain "yyyy-MM-dd" strings, exactly as an API consumer would send them.
 */
public final class TestData {

    private TestData() { }

    public static Map<String, Object> customer(String name, String email, String phone) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("phoneNumber", phone);
        return m;
    }

    public static Map<String, Object> flight(String number, String departure, String destination) {
        Map<String, Object> m = new HashMap<>();
        m.put("flightNumber", number);
        m.put("departure", departure);
        m.put("destination", destination);
        return m;
    }

    public static Map<String, Object> booking(Long customerId, Long flightId, Object date) {
        Map<String, Object> m = new HashMap<>();
        if (customerId != null) m.put("customerId", customerId);
        if (flightId != null) m.put("flightId", flightId);
        if (date != null) m.put("bookingDate", date.toString());
        return m;
    }

    public static String futureDate(int daysAhead) {
        return LocalDate.now().plusDays(daysAhead).toString();
    }

    /** Creates a Customer through the API and returns its generated id. */
    public static long createCustomer(String name, String email, String phone) {
        return given().contentType(ContentType.JSON).body(customer(name, email, phone))
                .when().post("/customers")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    /** Creates a Flight through the API and returns its generated id. */
    public static long createFlight(String number, String departure, String destination) {
        return given().contentType(ContentType.JSON).body(flight(number, departure, destination))
                .when().post("/flights")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    /** Creates a Booking through the API and returns its generated id. */
    public static long createBooking(long customerId, long flightId, String date) {
        return given().contentType(ContentType.JSON).body(booking(customerId, flightId, date))
                .when().post("/bookings")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }
}
