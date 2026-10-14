package com.helpdesk.tests.api.test;

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class CommentApiTest extends BaseApiTest {
     @Test(description = "Customer replies on own ticket")
    public void customerRepliesOnTicket()
    {
        TicketRequest ticket = new TicketRequest("comment","reply on own ticket");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");


        //Reply on the ticket created.
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body","comments"))

        .when()
        .post("/tickets/"+id+"/comments")

        .then()
        .statusCode(201)
        .body("body", equalTo("comments"));


    }

    // ---------- helpers used by the tests below ----------

    /**
     * Creates a ticket as the customer, then moves it through the given statuses as the agent.
     * createTicketInStatus()                           -> an OPEN ticket
     * createTicketInStatus("IN_PROGRESS", "RESOLVED")  -> a RESOLVED ticket
     */
    private int createTicketInStatus(String... statuses)
    {
        TicketRequest ticket = new TicketRequest("Comment test", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        for (String status : statuses) {
            given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
            .contentType("application/json")
            .body(Map.of("status", status))

            .when()
            .patch("/tickets/" + id)

            .then()
            .statusCode(200);
        }
        return id;
    }

    /** A ticket that belongs to a different customer: the agent raises it for another email. */
    private int createTicketForAnotherCustomer()
    {
        TicketRequest ticket = new TicketRequest("Someone else's ticket", "API automation test");
        ticket.setCustomerEmail("someone.else@test.com");
        return given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");
    }

    // ---------- adding comments ----------

    @Test(description = "Agent adds a public reply")
    public void agentAddsPublicReply()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "We are looking into it"))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(201)
        .body("body", equalTo("We are looking into it"))
        .body("ticketId", equalTo(id))
        .body("authorRole", equalTo("AGENT"))
        .body("internal", equalTo(false));
    }
    @Test(description = "Agent adds an internal note")
    public void agentAddsInternalNote()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Customer called twice already", "internal", true))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(201)
        .body("authorRole", equalTo("AGENT"))
        .body("internal", equalTo(true));
    }
    @Test(description = "Customer tries to add an internal note")
    public void customerTriesToAddInternalNote()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "Trying an internal note", "internal", true))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(403)
        .body("message", equalTo("Customers cannot add internal notes"));
    }

    // ---------- reading comments ----------

    @Test(description = "Customer never sees internal notes")
    public void customerNeverSeesInternalNotes()
    {
        int id = createTicketInStatus();

        // The agent adds one public reply and one internal note
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Public reply"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Internal note", "internal", true))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        // The customer gets only the public one
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/" + id + "/comments")

        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("body", hasItem("Public reply"))
        .body("body", not(hasItem("Internal note")))
        .body("internal", everyItem(equalTo(false)));
    }
    @Test(description = "Agent sees all comments, oldest first")
    public void agentSeesAllCommentsOldestFirst()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "First"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Second", "internal", true))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Third"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        // The agent sees all three, including the internal note, in the order they were written
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id + "/comments")

        .then()
        .statusCode(200)
        .body("body", contains("First", "Second", "Third"));
    }

    // ---------- another customer's ticket ----------

    @Test(description = "Customer comments on another customer's ticket")
    public void customerCommentsOnAnotherCustomersTicket()
    {
        int otherId = createTicketForAnotherCustomer();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "This is not my ticket"))

        .when()
        .post("/tickets/" + otherId + "/comments")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket " + otherId + " not found"));
    }
    @Test(description = "Customer reads comments of another customer's ticket")
    public void customerReadsCommentsOfAnotherCustomersTicket()
    {
        int otherId = createTicketForAnotherCustomer();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/" + otherId + "/comments")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket " + otherId + " not found"));
    }

    // ---------- validation ----------

    @DataProvider(name = "blankBodies")
    public Object[][] blankBodies() {
        return new Object[][] {
            {""},       // empty
            {"   "}     // only spaces
        };
    }
    @Test(dataProvider = "blankBodies", description = "Comment body blank")
    public void commentBodyBlank(String text)
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", text))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors.body", equalTo("must not be blank"));
    }
    @Test(description = "Comment body missing")
    public void commentBodyMissing()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body("{}")

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(400)
        .body("fieldErrors.body", equalTo("must not be blank"));
    }
    @Test(description = "Body with 5000 characters is accepted")
    public void commentWithFiveThousandCharBody()
    {
        int id = createTicketInStatus();
        String body5000 = "C".repeat(5000);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", body5000))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(201)
        .body("body", equalTo(body5000));
    }
    @Test(description = "Body with 5001 characters is rejected")
    public void commentWithFiveThousandOneCharBody()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "C".repeat(5001)))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(400)
        .body("fieldErrors.body", equalTo("size must be between 0 and 5000"));
    }

    // ---------- comments and ticket status ----------

    @Test(description = "No comments on a CLOSED ticket")
    public void noCommentsOnClosedTicket()
    {
        int id = createTicketInStatus("CLOSED");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "One more thing"))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(409)
        .body("message", equalTo("Ticket " + id + " is closed"));
    }
    @Test(description = "Customer reply moves WAITING_ON_CUSTOMER → IN_PROGRESS")
    public void customerReplyMovesWaitingToInProgress()
    {
        int id = createTicketInStatus("IN_PROGRESS", "WAITING_ON_CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "Here are the details"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"));
    }
    @Test(description = "Customer reply reopens a RESOLVED ticket")
    public void customerReplyReopensResolvedTicket()
    {
        int id = createTicketInStatus("IN_PROGRESS", "RESOLVED");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "It is still not working"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"))
        .body("resolvedAt", nullValue());
    }
    @Test(description = "Agent reply does not change status")
    public void agentReplyDoesNotChangeStatus()
    {
        int id = createTicketInStatus("IN_PROGRESS", "WAITING_ON_CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Reminder: we still need the details"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("WAITING_ON_CUSTOMER"));
    }
    @Test(description = "Customer reply on an OPEN ticket does not change status")
    public void customerReplyOnOpenTicketDoesNotChangeStatus()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "Any update?"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("OPEN"));
    }

    // ---------- missing ticket and missing token ----------

    @Test(description = "Comment on a ticket that does not exist")
    public void commentOnTicketThatDoesNotExist()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Hello"))

        .when()
        .post("/tickets/999999999/comments")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket 999999999 not found"));
    }
    @Test(description = "Comment without a token")
    public void commentWithoutToken()
    {
        given()
        .contentType("application/json")
        .body(Map.of("body", "Hello"))

        .when()
        .post("/tickets/999999999/comments")

        .then()
        .statusCode(401)
        .body("message", equalTo("Missing or invalid token"));
    }
}
