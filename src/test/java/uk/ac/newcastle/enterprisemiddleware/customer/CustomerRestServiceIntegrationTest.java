package uk.ac.newcastle.enterprisemiddleware.customer;

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
import static uk.ac.newcastle.enterprisemiddleware.TestData.customer;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTestResource(H2DatabaseTestResource.class)
class CustomerRestServiceIntegrationTest {

    private static final String EMAIL = "customer.test@example.com";
    private static long customerId;

    @Test
    @Order(1)
    void canCreateCustomer() {
        customerId = given().contentType(ContentType.JSON)
                .body(customer("Alice Smith", EMAIL, "07123456789"))
                .when().post("/customers")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Alice Smith"))
                .body("email", equalTo(EMAIL))
                .body("phoneNumber", equalTo("07123456789"))
                .extract().jsonPath().getLong("id");
    }

    @Test
    @Order(2)
    void canListCustomers() {
        when().get("/customers")
                .then().statusCode(200)
                .body("email", hasItem(EMAIL));
    }

    @Test
    @Order(3)
    void canGetCustomerById() {
        when().get("/customers/" + customerId)
                .then().statusCode(200)
                .body("email", equalTo(EMAIL));
        when().get("/customers/999999")
                .then().statusCode(404);
    }

    @Test
    @Order(4)
    void idSuppliedByClientIsIgnored() {
        var body = customer("Bob Jones", "bob.id@example.com", "07000000001");
        body.put("id", 424242);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/customers")
                .then().statusCode(201)
                .body("id", not(equalTo(424242)));
    }

    @Test
    @Order(5)
    void cannotCreateInvalidCustomers() {
        // name with digits
        given().contentType(ContentType.JSON).body(customer("Al1ce", "a1@example.com", "07123456789"))
                .when().post("/customers").then().statusCode(400).body("reasons.name", notNullValue());
        // name 50+ characters
        given().contentType(ContentType.JSON).body(customer("A".repeat(50), "a2@example.com", "07123456789"))
                .when().post("/customers").then().statusCode(400);
        // empty name
        given().contentType(ContentType.JSON).body(customer("", "a3@example.com", "07123456789"))
                .when().post("/customers").then().statusCode(400);
        // invalid email
        given().contentType(ContentType.JSON).body(customer("Alice", "not-an-email", "07123456789"))
                .when().post("/customers").then().statusCode(400).body("reasons.email", notNullValue());
        // phone not starting with 0
        given().contentType(ContentType.JSON).body(customer("Alice", "a4@example.com", "17123456789"))
                .when().post("/customers").then().statusCode(400).body("reasons.phoneNumber", notNullValue());
        // phone wrong length
        given().contentType(ContentType.JSON).body(customer("Alice", "a5@example.com", "0712345"))
                .when().post("/customers").then().statusCode(400);
        // missing fields
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/customers").then().statusCode(400);
    }

    @Test
    @Order(6)
    void cannotCreateCustomerWithDuplicateEmail() {
        given().contentType(ContentType.JSON).body(customer("Another Person", EMAIL, "07999999999"))
                .when().post("/customers")
                .then().statusCode(409)
                .body("reasons.email", notNullValue());
    }

    @Test
    @Order(7)
    void canDeleteCustomer() {
        when().delete("/customers/" + customerId).then().statusCode(204);
        when().get("/customers/" + customerId).then().statusCode(404);
        when().delete("/customers/" + customerId).then().statusCode(404);
    }
}
