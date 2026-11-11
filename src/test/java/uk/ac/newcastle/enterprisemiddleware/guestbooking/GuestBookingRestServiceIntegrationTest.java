package uk.ac.newcastle.enterprisemiddleware.guestbooking;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;
import static uk.ac.newcastle.enterprisemiddleware.TestData.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTestResource(H2DatabaseTestResource.class)
class GuestBookingRestServiceIntegrationTest {

    private static final String DATE = futureDate(90);
    private static long flightId;

    private static Map<String, Object> guestBooking(Map<String, Object> customer, Map<String, Object> booking) {
        Map<String, Object> body = new HashMap<>();
        body.put("customer", customer);
        body.put("booking", booking);
        return body;
    }

    private static void assertNoCustomerWithEmail(String email) {
        when().get("/customers").then().statusCode(200).body("email", not(hasItem(email)));
    }

    @Test
    @Order(1)
    void setUpFlight() {
        flightId = createFlight("GB001", "NCL", "CDG");
    }

    @Test
    @Order(2)
    void canCreateGuestBooking() {
        String email = "guest.ok@example.com";
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Guest User", email, "07444444444"), booking(null, flightId, DATE)))
                .when().post("/guestbookings")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("customer.email", equalTo(email))
                .body("flightId", equalTo((int) flightId))
                .body("bookingDate", equalTo(DATE));

        // both the customer and the booking were committed
        when().get("/customers").then().body("email", hasItem(email));
    }

    @Test
    @Order(3)
    void validCustomerWithPastDateIsRolledBack() {
        String email = "guest.pastdate@example.com";
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Guest Past", email, "07555555555"),
                        booking(null, flightId, LocalDate.now().minusDays(5))))
                .when().post("/guestbookings")
                .then().statusCode(400)
                .body("reasons", hasKey("booking.bookingDate"));

        // the customer must NOT have been saved - the transaction was rolled back
        assertNoCustomerWithEmail(email);
    }

    @Test
    @Order(4)
    void validCustomerWithUnknownFlightIsRolledBack() {
        String email = "guest.noflight@example.com";
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Guest NoFlight", email, "07666666666"), booking(null, 999999L, futureDate(91))))
                .when().post("/guestbookings")
                .then().statusCode(400)
                .body("reasons", hasKey("booking.flightId"));
        assertNoCustomerWithEmail(email);
    }

    @Test
    @Order(5)
    void validCustomerWithAlreadyBookedSlotIsRolledBack() {
        String email = "guest.clash@example.com";
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Guest Clash", email, "07777777777"), booking(null, flightId, DATE)))
                .when().post("/guestbookings")
                .then().statusCode(409);
        assertNoCustomerWithEmail(email);
    }

    @Test
    @Order(6)
    void invalidCustomerIsRejected() {
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Bad 123", "guest.badname@example.com", "07888888888"),
                        booking(null, flightId, futureDate(92))))
                .when().post("/guestbookings")
                .then().statusCode(400)
                .body("reasons", hasKey("customer.name"));
        assertNoCustomerWithEmail("guest.badname@example.com");
    }

    @Test
    @Order(7)
    void missingPartsAreRejected() {
        given().contentType(ContentType.JSON)
                .body(guestBooking(customer("Only Customer", "guest.only@example.com", "07999999990"), null))
                .when().post("/guestbookings")
                .then().statusCode(400);
        assertNoCustomerWithEmail("guest.only@example.com");
    }
}
