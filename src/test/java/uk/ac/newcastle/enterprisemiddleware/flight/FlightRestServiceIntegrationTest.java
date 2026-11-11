package uk.ac.newcastle.enterprisemiddleware.flight;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;
import static uk.ac.newcastle.enterprisemiddleware.TestData.flight;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTestResource(H2DatabaseTestResource.class)
class FlightRestServiceIntegrationTest {

    private static long flightId;

    @Test
    @Order(1)
    void canCreateFlight() {
        flightId = given().contentType(ContentType.JSON).body(flight("FT001", "NCL", "LHR"))
                .when().post("/flights")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("flightNumber", equalTo("FT001"))
                .body("departure", equalTo("NCL"))
                .body("destination", equalTo("LHR"))
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(2)
    void canListFlights() {
        when().get("/flights")
                .then().statusCode(200)
                .body("flightNumber", hasItem("FT001"));
    }

    @Test
    @Order(3)
    void canGetFlightById() {
        when().get("/flights/" + flightId).then().statusCode(200).body("flightNumber", equalTo("FT001"));
        when().get("/flights/999999").then().statusCode(404);
    }

    @Test
    @Order(4)
    void cannotCreateInvalidFlights() {
        // flight number too short / too long / not alphanumeric
        given().contentType(ContentType.JSON).body(flight("FT01", "NCL", "LHR"))
                .when().post("/flights").then().statusCode(400).body("reasons.flightNumber", notNullValue());
        given().contentType(ContentType.JSON).body(flight("FT0001", "NCL", "LHR"))
                .when().post("/flights").then().statusCode(400);
        given().contentType(ContentType.JSON).body(flight("FT-01", "NCL", "LHR"))
                .when().post("/flights").then().statusCode(400);
        // departure lower case / wrong length
        given().contentType(ContentType.JSON).body(flight("FT002", "ncl", "LHR"))
                .when().post("/flights").then().statusCode(400).body("reasons.departure", notNullValue());
        given().contentType(ContentType.JSON).body(flight("FT003", "NCL", "LHRX"))
                .when().post("/flights").then().statusCode(400).body("reasons.destination", notNullValue());
        // destination the same as departure
        given().contentType(ContentType.JSON).body(flight("FT004", "NCL", "NCL"))
                .when().post("/flights").then().statusCode(400);
        // missing fields
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/flights").then().statusCode(400);
    }

    @Test
    @Order(5)
    void cannotCreateFlightWithDuplicateNumber() {
        given().contentType(ContentType.JSON).body(flight("FT001", "MAN", "EDI"))
                .when().post("/flights")
                .then().statusCode(409)
                .body("reasons.flightNumber", notNullValue());
    }

    @Test
    @Order(6)
    void canDeleteFlight() {
        when().delete("/flights/" + flightId).then().statusCode(204);
        when().get("/flights/" + flightId).then().statusCode(404);
        when().delete("/flights/" + flightId).then().statusCode(404);
    }
}
