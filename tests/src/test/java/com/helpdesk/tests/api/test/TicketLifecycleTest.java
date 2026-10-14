package com.helpdesk.tests.api.test;

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class TicketLifecycleTest extends BaseApiTest {
    @Test(description = "OPEN → IN_PROGRESS auto-assigns to the agent")
    public void inprogessTicketsAutoAssign()
    {
        TicketRequest ticket = new TicketRequest("auto assign","OPEN → IN_PROGRESS auto-assigns to the agent");
        int id = given()
        .header("Authorization","Bearer "+TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets/")

        .then()
        .statusCode(201)
        .extract().path("id");

        int agentId = given()
        .header("Authorization","Bearer "+TokenManager.getToken("agent"))
        .contentType("application/json")

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        given()
        .header("Authorization","Bearer "+TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status","IN_PROGRESS"))

        .when()
        .patch("/tickets"+id)

        .then()
        .statusCode(200)
        .body("assignee.id",equalTo(agentId));        

        


    }
    @Test(description = "OPEN → IN_PROGRESS keeps an existing assignee")
    public void inProgressKeepsExistingAssignee()
    {
        // The second agent will own the ticket before anyone starts work on it
        int agent2Id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent2"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        TicketRequest ticket = new TicketRequest("Keep assignee", "OPEN → IN_PROGRESS keeps an existing assignee");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 1. Assign the ticket to agent2
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("assigneeId", agent2Id))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("assignee.id", equalTo(agent2Id));

        // 2. A different agent moves it to IN_PROGRESS: agent2 must stay the assignee
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "IN_PROGRESS"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"))
        .body("assignee.id", equalTo(agent2Id));
    }

    // ---------- helper used by the status tests below ----------

    /**
     * Creates a ticket as the customer, then moves it through the given statuses as the agent.
     * createTicketInStatus()                              -> an OPEN ticket
     * createTicketInStatus("IN_PROGRESS", "RESOLVED")     -> a RESOLVED ticket
     */
    private int createTicketInStatus(String... statuses)
    {
        TicketRequest ticket = new TicketRequest("Lifecycle test", "API automation test");
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
            .statusCode(200)
            .body("status", equalTo(status));
        }
        return id;
    }

    // ---------- allowed moves ----------

    @Test(description = "OPEN → CLOSED")
    public void openToClosed()
    {
        int id = createTicketInStatus();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "CLOSED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("CLOSED"))
        .body("closedAt", notNullValue());
    }
    @Test(description = "IN_PROGRESS → WAITING_ON_CUSTOMER")
    public void inProgressToWaitingOnCustomer()
    {
        int id = createTicketInStatus("IN_PROGRESS");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "WAITING_ON_CUSTOMER"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("WAITING_ON_CUSTOMER"));
    }
    @Test(description = "IN_PROGRESS → RESOLVED sets resolvedAt")
    public void inProgressToResolvedSetsResolvedAt()
    {
        int id = createTicketInStatus("IN_PROGRESS");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "RESOLVED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("RESOLVED"))
        .body("resolvedAt", notNullValue());
    }
    @Test(description = "WAITING_ON_CUSTOMER → IN_PROGRESS")
    public void waitingOnCustomerToInProgress()
    {
        int id = createTicketInStatus("IN_PROGRESS", "WAITING_ON_CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "IN_PROGRESS"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"));
    }
    @Test(description = "WAITING_ON_CUSTOMER → RESOLVED")
    public void waitingOnCustomerToResolved()
    {
        int id = createTicketInStatus("IN_PROGRESS", "WAITING_ON_CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "RESOLVED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("RESOLVED"))
        .body("resolvedAt", notNullValue());
    }
    @Test(description = "RESOLVED → IN_PROGRESS (reopen) clears resolvedAt")
    public void resolvedToInProgressClearsResolvedAt()
    {
        int id = createTicketInStatus("IN_PROGRESS", "RESOLVED");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "IN_PROGRESS"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"))
        .body("resolvedAt", nullValue());
    }
    @Test(description = "RESOLVED → CLOSED sets closedAt")
    public void resolvedToClosedSetsClosedAt()
    {
        int id = createTicketInStatus("IN_PROGRESS", "RESOLVED");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "CLOSED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("CLOSED"))
        .body("closedAt", notNullValue())
        .body("resolvedAt", notNullValue());
    }

    // ---------- moves that are not allowed ----------

    @DataProvider(name = "invalidMoves")
    public Object[][] invalidMoves() {
        return new Object[][] {
            // statuses to reach the starting point,                 start,                 not allowed
            {new String[]{},                                     "OPEN",                "WAITING_ON_CUSTOMER"},
            {new String[]{},                                     "OPEN",                "RESOLVED"},
            {new String[]{"IN_PROGRESS"},                        "IN_PROGRESS",         "OPEN"},
            {new String[]{"IN_PROGRESS"},                        "IN_PROGRESS",         "CLOSED"},
            {new String[]{"IN_PROGRESS", "WAITING_ON_CUSTOMER"}, "WAITING_ON_CUSTOMER", "OPEN"},
            {new String[]{"IN_PROGRESS", "WAITING_ON_CUSTOMER"}, "WAITING_ON_CUSTOMER", "CLOSED"},
            {new String[]{"IN_PROGRESS", "RESOLVED"},            "RESOLVED",            "OPEN"},
            {new String[]{"IN_PROGRESS", "RESOLVED"},            "RESOLVED",            "WAITING_ON_CUSTOMER"},
            {new String[]{"CLOSED"},                             "CLOSED",              "OPEN"},
            {new String[]{"CLOSED"},                             "CLOSED",              "IN_PROGRESS"},
            {new String[]{"CLOSED"},                             "CLOSED",              "WAITING_ON_CUSTOMER"},
            {new String[]{"CLOSED"},                             "CLOSED",              "RESOLVED"}
        };
    }
    @Test(dataProvider = "invalidMoves", description = "Invalid status moves are rejected")
    public void invalidStatusMoveIsRejected(String[] path, String from, String to)
    {
        int id = createTicketInStatus(path);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", to))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(409)
        .body("message", startsWith("Cannot move ticket from " + from + " to " + to));

        // The ticket is still in the status it had before
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo(from));
    }
    @Test(description = "Failed transition leaves the ticket unchanged")
    public void failedTransitionLeavesTicketUnchanged()
    {
        int id = createTicketInStatus();          // OPEN, MEDIUM, no category, no assignee

        String updatedAtBefore = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .extract().path("updatedAt");

        // OPEN -> RESOLVED is not allowed, so the whole request must be refused,
        // including the priority and category changes sent with it
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "RESOLVED", "priority", "URGENT", "category", "BILLING"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(409);

        // Nothing was saved
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("OPEN"))
        .body("priority", equalTo("MEDIUM"))
        .body("category", nullValue())
        .body("assignee", nullValue())
        .body("updatedAt", equalTo(updatedAtBefore));
    }
    @Test(description = "Full happy path: OPEN → IN_PROGRESS → WAITING → (customer reply) IN_PROGRESS → RESOLVED → CLOSED")
    public void fullHappyPath()
    {
        // 1. Customer creates a ticket: OPEN
        TicketRequest ticket = new TicketRequest("Full happy path", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("status", equalTo("OPEN"))
        .extract().path("id");

        // 2. Agent starts work: IN_PROGRESS
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "IN_PROGRESS"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"));

        // 3. Agent asks the customer for details: WAITING_ON_CUSTOMER
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "WAITING_ON_CUSTOMER"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("WAITING_ON_CUSTOMER"));

        // 4. Customer replies with a comment
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "Here are the details you asked for"))

        .when()
        .post("/tickets/" + id + "/comments")

        .then()
        .statusCode(201);

        // 5. The reply moved the ticket back to IN_PROGRESS by itself
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"));

        // 6. Agent resolves it: RESOLVED
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "RESOLVED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("RESOLVED"))
        .body("resolvedAt", notNullValue());

        // 7. Agent closes it: CLOSED
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "CLOSED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("CLOSED"))
        .body("closedAt", notNullValue());
    }
}
