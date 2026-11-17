package uk.ac.newcastle.enterprisemiddleware.travelagent;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.newcastle.enterprisemiddleware.TestData.*;

/**
 * Tests for the TravelAgent (Part 3). The Taxi and Hotel services are replaced with fakes, so the tests do not depend
 * on colleagues' live services and can make any step fail on purpose.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTestResource(H2DatabaseTestResource.class)
class TravelAgentRestServiceIntegrationTest {

    private static final String DATE = futureDate(300);

    private static long customerId;
    private static long flightId;
    private static long firstTravelAgentBookingId;

    private FakeTaxiGateway taxi;
    private FakeHotelGateway hotel;

    @BeforeEach
    void installFakeServices() {
        taxi = new FakeTaxiGateway();
        hotel = new FakeHotelGateway();
        QuarkusMock.installMockForType(taxi, TaxiGateway.class);
        QuarkusMock.installMockForType(hotel, HotelGateway.class);
    }

    private static Map<String, Object> request(Long customer, Long flight, Long taxiId, Long hotelId, Object date) {
        Map<String, Object> body = new HashMap<>();
        if (customer != null) body.put("customerId", customer);
        if (flight != null) body.put("flightId", flight);
        if (taxiId != null) body.put("taxiId", taxiId);
        if (hotelId != null) body.put("hotelId", hotelId);
        if (date != null) body.put("bookingDate", date.toString());
        return body;
    }

    private static int localBookingsFor(long customer) {
        return given().queryParam("customerId", customer).when().get("/bookings")
                .then().statusCode(200).extract().jsonPath().getList("$").size();
    }

    @Test
    @Order(1)
    void setUpCustomerAndFlight() {
        customerId = createCustomer("Agent Tester", "ta.tester@example.com", "07555000001");
        flightId = createFlight("TAF01", "NCL", "CDG");
    }

    @Test
    @Order(2)
    void canBookFlightTaxiAndHotelTogether() {
        firstTravelAgentBookingId = given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, DATE))
                .when().post("/travelagent/bookings")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("customerId", equalTo((int) customerId))
                .body("flightId", equalTo((int) flightId))
                .body("taxiId", equalTo(5))
                .body("hotelId", equalTo(7))
                .body("bookingDate", equalTo(DATE))
                .body("flightBookingId", notNullValue())
                .body("taxiBookingId", equalTo(taxi.booked.get(0).intValue()))
                .body("hotelBookingId", equalTo(hotel.booked.get(0).intValue()))
                .extract().jsonPath().getLong("id");

        assertEquals(1, taxi.booked.size());
        assertEquals(1, hotel.booked.size());
        assertTrue(taxi.cancelled.isEmpty() && hotel.cancelled.isEmpty(), "nothing should be cancelled on success");
        assertEquals(1, localBookingsFor(customerId), "the flight should be booked locally");
    }

    @Test
    @Order(3)
    void canListAndFetchTravelAgentBookings() {
        given().queryParam("customerId", customerId).when().get("/travelagent/bookings")
                .then().statusCode(200)
                .body("size()", is(1))
                .body("[0].id", equalTo((int) firstTravelAgentBookingId));
        when().get("/travelagent/bookings").then().statusCode(200).body("id", hasItem((int) firstTravelAgentBookingId));
        when().get("/travelagent/bookings/" + firstTravelAgentBookingId).then().statusCode(200);
        when().get("/travelagent/bookings/999999").then().statusCode(404);
        given().queryParam("customerId", 999999).when().get("/travelagent/bookings").then().statusCode(404);
    }

    /** The coursework scenario: the first two bookings succeed, the third fails, and the first two are cancelled. */
    @Test
    @Order(4)
    void whenFlightFailsTheTaxiAndHotelAreCancelled() {
        // The flight is already booked on DATE (test 2), so step 3 fails after taxi and hotel have been booked.
        given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, DATE))
                .when().post("/travelagent/bookings")
                .then().statusCode(409)
                .body("reasons.flight", notNullValue())
                .body("reasons.compensation", containsString("cancelled"));

        assertEquals(1, taxi.booked.size(), "taxi was booked first");
        assertEquals(1, hotel.booked.size(), "hotel was booked second");
        assertEquals(taxi.booked, taxi.cancelled, "the taxi booking must be cancelled");
        assertEquals(hotel.booked, hotel.cancelled, "the hotel booking must be cancelled");
        given().queryParam("customerId", customerId).when().get("/travelagent/bookings")
                .then().statusCode(200).body("size()", is(1));
    }

    @Test
    @Order(5)
    void whenHotelFailsTheTaxiIsCancelledAndNoFlightIsBooked() {
        hotel.failBookingWith = new RemoteServiceException("Hotel", "book hotel 7", 409, "Hotel is already booked on that date");

        given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, futureDate(310)))
                .when().post("/travelagent/bookings")
                .then().statusCode(409)
                .body("reasons.hotel", notNullValue());

        assertEquals(1, taxi.booked.size());
        assertEquals(taxi.booked, taxi.cancelled, "the taxi booking must be cancelled");
        assertTrue(hotel.cancelled.isEmpty(), "the hotel was never booked");
        assertEquals(1, localBookingsFor(customerId), "no extra flight booking may be left behind");
    }

    @Test
    @Order(6)
    void whenTaxiServiceIsDownNothingIsBooked() {
        taxi.failBookingWith = new RemoteServiceException("Taxi", "book taxi 5", 0, "connection refused");

        given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, futureDate(320)))
                .when().post("/travelagent/bookings")
                .then().statusCode(502)
                .body("reasons.taxi", notNullValue());

        assertTrue(hotel.booked.isEmpty(), "the hotel must not be booked if the taxi failed");
        assertEquals(1, localBookingsFor(customerId));
    }

    @Test
    @Order(7)
    void invalidRequestsAreRejectedBeforeContactingOtherServices() {
        given().contentType(ContentType.JSON).body(request(999999L, flightId, 5L, 7L, futureDate(330)))
                .when().post("/travelagent/bookings").then().statusCode(400).body("reasons.customerId", notNullValue());
        given().contentType(ContentType.JSON).body(request(customerId, 999999L, 5L, 7L, futureDate(330)))
                .when().post("/travelagent/bookings").then().statusCode(400).body("reasons.flightId", notNullValue());
        given().contentType(ContentType.JSON).body(request(customerId, flightId, 5L, 7L, LocalDate.now().minusDays(1)))
                .when().post("/travelagent/bookings").then().statusCode(400).body("reasons.bookingDate", notNullValue());
        given().contentType(ContentType.JSON).body(request(customerId, flightId, null, 7L, futureDate(330)))
                .when().post("/travelagent/bookings").then().statusCode(400).body("reasons.taxiId", notNullValue());
        given().contentType(ContentType.JSON).body(request(customerId, flightId, 5L, null, futureDate(330)))
                .when().post("/travelagent/bookings").then().statusCode(400).body("reasons.hotelId", notNullValue());

        assertTrue(taxi.booked.isEmpty() && hotel.booked.isEmpty(), "invalid requests must not book anything remotely");
    }

    @Test
    @Order(8)
    void canCancelTravelAgentBooking() {
        var created = given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, futureDate(340)))
                .when().post("/travelagent/bookings")
                .then().statusCode(201).extract().jsonPath();
        long id = created.getLong("id");
        long flightBookingId = created.getLong("flightBookingId");

        when().delete("/travelagent/bookings/" + id).then().statusCode(204);

        assertEquals(taxi.booked, taxi.cancelled, "taxi booking cancelled remotely");
        assertEquals(hotel.booked, hotel.cancelled, "hotel booking cancelled remotely");
        when().get("/travelagent/bookings/" + id).then().statusCode(404);
        when().get("/bookings/" + flightBookingId).then().statusCode(404);
        when().delete("/travelagent/bookings/" + id).then().statusCode(404);
    }

    @Test
    @Order(9)
    void ifARemoteCancelFailsTheBookingIsKept() {
        long id = given().contentType(ContentType.JSON)
                .body(request(customerId, flightId, 5L, 7L, futureDate(350)))
                .when().post("/travelagent/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("id");

        hotel.failCancelWith = new RemoteServiceException("Hotel", "cancel booking", 0, "connection refused");
        when().delete("/travelagent/bookings/" + id).then().statusCode(502).body("reasons.hotel", notNullValue());
        when().get("/travelagent/bookings/" + id).then().statusCode(200);

        hotel.failCancelWith = null;
        when().delete("/travelagent/bookings/" + id).then().statusCode(204);
    }

    @Test
    @Order(10)
    void deletingACustomerRemovesTheirTravelAgentBookings() {
        long other = createCustomer("Cascade Agent", "ta.cascade@example.com", "07555000002");
        long id = given().contentType(ContentType.JSON)
                .body(request(other, flightId, 5L, 7L, futureDate(360)))
                .when().post("/travelagent/bookings")
                .then().statusCode(201).extract().jsonPath().getLong("id");

        when().delete("/customers/" + other).then().statusCode(204);
        when().get("/travelagent/bookings/" + id).then().statusCode(404);
    }
}
