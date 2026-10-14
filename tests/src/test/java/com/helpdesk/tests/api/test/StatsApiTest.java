package com.helpdesk.tests.api.test;

import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import io.restassured.response.Response;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class StatsApiTest extends BaseApiTest {
    @Test(description = "Agent gets dashboard counts")
    public void agentGetsDashboardCounts()
    {
        given()
        .header("Authorization","Bearer "+TokenManager.getToken("agent"))
        .contentType("application/json")

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .body("open", greaterThanOrEqualTo(0))
        .body("inProgress", greaterThanOrEqualTo(0))
        .body("waitingOnCustomer", greaterThanOrEqualTo(0))
        .body("resolved", greaterThanOrEqualTo(0))
        .body("closed", greaterThanOrEqualTo(0))
        .body("slaBreached", greaterThanOrEqualTo(0))
        .body("unassigned", greaterThanOrEqualTo(0));
    }
    @Test(description = "Open count increases by one when a new ticket is raised")
    public void openCountIncreases()
    {
        int openCount = given()
        .header("Authorization","Bearer "+TokenManager.getToken("agent"))
        .contentType("application/json")

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .body("open", greaterThanOrEqualTo(0))
        .extract().path("open");

        // Customer raises one new ticket: it starts as OPEN
        TicketRequest ticket = new TicketRequest("Stats count", "API automation test");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("status", equalTo("OPEN"));


        //Verify that the count is increased by one;
        int newCount = given()
        .header("Authorization","Bearer "+TokenManager.getToken("agent"))
        .contentType("application/json")

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
         .extract().path("open");
         Assert.assertEquals(newCount, openCount + 1);

        

    }
    @Test(description = "Counts move when a ticket is picked up")
    public void countsMoveWhenTicketIsPickedUp()
    {
        // A new OPEN, unassigned ticket to pick up
        TicketRequest ticket = new TicketRequest("Stats pick up", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // Counts before the agent picks it up
        Response before = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .extract().response();

        int open = before.path("open");
        int inProgress = before.path("inProgress");
        int unassigned = before.path("unassigned");

        // Agent picks the ticket up: OPEN -> IN_PROGRESS, and it becomes assigned to the agent
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "IN_PROGRESS"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200);

        // One ticket left "open" and "unassigned", and joined "inProgress"
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .body("open", equalTo(open - 1))
        .body("inProgress", equalTo(inProgress + 1))
        .body("unassigned", equalTo(unassigned - 1));
    }
    @Test(description = "Customer cannot see stats")
    public void customerCannotSeeStats()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));
    }
}
