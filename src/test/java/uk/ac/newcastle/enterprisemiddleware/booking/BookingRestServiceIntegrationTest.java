package uk.ac.newcastle.enterprisemiddleware.booking;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;
import static uk.ac.newcastle.enterprisemiddleware.TestData.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTestResource(H2DatabaseTestResource.class)
class BookingRestServiceIntegrationTest {

    private static final String DATE = futureDate(30);

    private static long customerId;
    private static long flightId;
    private static long bookingId;

    @Test
    @Order(1)
    void setUpCustomerAndFlight() {
        customerId = createCustomer("Booking Tester", "booking.test@example.com", "07111111111");
        flightId = createFlight("BK001", "NCL", "AMS");
    }

    @Test
    @Order(2)
    void canCreateBooking() {
        bookingId = given().contentType(ContentType.JSON).body(booking(customerId, flightId, DATE))
                .when().post("/bookings")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("customerId", equalTo((int) customerId))
                .body("flightId", equalTo((int) flightId))
                .body("bookingDate", equalTo(DATE))
                .body("flight.flightNumber", equalTo("BK001"))
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(3)
    void canListBookingsForCustomer() {
        given().queryParam("customerId", customerId)
                .when().get("/bookings")
                .then().statusCode(200)
                .body("size()", is(1))
                .body("[0].id", equalTo((int) bookingId));

        when().get("/bookings").then().statusCode(200).body("id", hasItem((int) bookingId));
        when().get("/bookings/" + bookingId).then().statusCode(200);
        when().get("/bookings/999999").then().statusCode(404);
    }

    @Test
    @Order(4)
    void listingBookingsForUnknownCustomerGives404() {
        given().queryParam("customerId", 999999).when().get("/bookings").then().statusCode(404);
    }

    @Test
    @Order(5)
    void cannotDoubleBookSameFlightAndDate() {
        long other = createCustomer("Second Tester", "second.booking@example.com", "07222222222");
        given().contentType(ContentType.JSON).body(booking(other, flightId, DATE))
                .when().post("/bookings")
                .then().statusCode(409)
                .body("reasons.bookingDate", notNullValue());
    }

    @Test
    @Order(6)
    void cannotCreateInvalidBookings() {
        // date in the past
        given().contentType(ContentType.JSON).body(booking(customerId, flightId, LocalDate.now().minusDays(1)))
                .when().post("/bookings").then().statusCode(400).body("reasons.bookingDate", notNullValue());
        // today is not "in the future"
        given().contentType(ContentType.JSON).body(booking(customerId, flightId, LocalDate.now()))
                .when().post("/bookings").then().statusCode(400);
        // missing date
        given().contentType(ContentType.JSON).body(booking(customerId, flightId, null))
                .when().post("/bookings").then().statusCode(400);
        // malformed date
        given().contentType(ContentType.JSON).body(booking(customerId, flightId, "15-01-2030"))
                .when().post("/bookings").then().statusCode(400);
        // unknown / missing customer
        given().contentType(ContentType.JSON).body(booking(999999L, flightId, futureDate(40)))
                .when().post("/bookings").then().statusCode(400).body("reasons.customerId", notNullValue());
        given().contentType(ContentType.JSON).body(booking(null, flightId, futureDate(40)))
                .when().post("/bookings").then().statusCode(400);
        // unknown / missing flight
        given().contentType(ContentType.JSON).body(booking(customerId, 999999L, futureDate(40)))
                .when().post("/bookings").then().statusCode(400).body("reasons.flightId", notNullValue());
        given().contentType(ContentType.JSON).body(booking(customerId, null, futureDate(40)))
                .when().post("/bookings").then().statusCode(400);
    }

    @Test
    @Order(7)
    void canCancelAndRemakeBooking() {
        when().delete("/bookings/" + bookingId).then().statusCode(204);
        when().get("/bookings/" + bookingId).then().statusCode(404);
        when().delete("/bookings/" + bookingId).then().statusCode(404);

        // the flight/date slot is free again, so the booking can be remade
        bookingId = createBooking(customerId, flightId, DATE);
        given().queryParam("customerId", customerId).when().get("/bookings")
                .then().statusCode(200).body("size()", is(1));
    }

    @Test
    @Order(8)
    void deletingCustomerDeletesTheirBookings() {
        long cust = createCustomer("Cascade Customer", "cascade.customer@example.com", "07333333333");
        long b1 = createBooking(cust, flightId, futureDate(50));
        long b2 = createBooking(cust, flightId, futureDate(51));

        when().delete("/customers/" + cust).then().statusCode(204);

        when().get("/bookings/" + b1).then().statusCode(404);
        when().get("/bookings/" + b2).then().statusCode(404);
        // the flight itself is untouched
        when().get("/flights/" + flightId).then().statusCode(200);
    }

    @Test
    @Order(9)
    void deletingFlightDeletesItsBookings() {
        long flight = createFlight("BK002", "LHR", "JFK");
        long b1 = createBooking(customerId, flight, futureDate(60));

        when().delete("/flights/" + flight).then().statusCode(204);

        when().get("/bookings/" + b1).then().statusCode(404);
        // the customer and their other booking are untouched
        when().get("/customers/" + customerId).then().statusCode(200);
        when().get("/bookings/" + bookingId).then().statusCode(200);
    }
}
