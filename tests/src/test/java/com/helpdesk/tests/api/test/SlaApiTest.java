package com.helpdesk.tests.api.test;

import io.restassured.response.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import static io.restassured.RestAssured.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * These tests need the app started with short SLA settings:
 *   cd backend && SLA_URGENT=PT1M SLA_CHECK_INTERVAL=PT10S mvn spring-boot:run
 * Then run them with:   cd tests && mvn test -Dtest=SlaApiTest
 */
public class SlaApiTest extends BaseApiTest {

    // Tickets prepared once for the whole class, so the tests wait for the deadline only one time
    private int openTicketId;         // URGENT, left OPEN            -> must become breached
    private int inProgressTicketId;   // URGENT, moved to IN_PROGRESS -> must become breached
    private int waitingTicketId;      // URGENT, WAITING_ON_CUSTOMER  -> must NOT be flagged
    private int resolvedTicketId;     // URGENT, RESOLVED in time     -> must NOT be flagged
    private int statsTicketId;        // URGENT, left OPEN            -> used by the stats test

    /** Creates a ticket as the customer, makes it URGENT, then moves it through the given statuses. */
    private int createUrgentTicket(String... statuses)
    {
        TicketRequest ticket = new TicketRequest("SLA test", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)
        .when()
        .post("/tickets")
        .then()
        .statusCode(201)
        .extract().path("id");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "URGENT"))
        .when()
        .patch("/tickets/" + id)
        .then()
        .statusCode(200);

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

    private void waitUntilBreached(int id)
    {
        await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(() ->
            given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
            .when()
            .get("/tickets/" + id)
            .then()
            .statusCode(200)
            .body("sla.breached", equalTo(true)));
    }

    @BeforeClass(alwaysRun = true)
    public void createTicketsAndWaitForTheDeadline()
    {
        openTicketId = createUrgentTicket();
        inProgressTicketId = createUrgentTicket("IN_PROGRESS");
        waitingTicketId = createUrgentTicket("IN_PROGRESS", "WAITING_ON_CUSTOMER");
        resolvedTicketId = createUrgentTicket("IN_PROGRESS", "RESOLVED");
        statsTicketId = createUrgentTicket();

        // All five were created together, so once these are breached the deadline has passed for all of them
        waitUntilBreached(openTicketId);
        waitUntilBreached(inProgressTicketId);
        waitUntilBreached(statsTicketId);
    }

    @Test(groups = "sla", description = "OPEN ticket past its due time becomes breached")
    public void openTicketPastDueBecomesBreached()
    {
        // 1. Customer creates a ticket: OPEN, not breached
        TicketRequest ticket = new TicketRequest("SLA breach", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("sla.breached", equalTo(false))
        .extract().path("id");

        // 2. Agent makes it URGENT: with the short settings it is due 1 minute after creation
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "URGENT"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("OPEN"));

        // 3. Keep checking every 5 seconds until the background job marks it as breached
        await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(() ->
            given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

            .when()
            .get("/tickets/" + id)

            .then()
            .statusCode(200)
            .body("sla.breached", equalTo(true))
            .body("sla.breachedAt", notNullValue())
            .body("status", equalTo("OPEN")));
    }

    @Test(groups = "sla", description = "Breach adds one internal SYSTEM note")
    public void breachAddsOneInternalSystemNote()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + openTicketId + "/comments")

        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].authorRole", equalTo("SYSTEM"))
        .body("[0].internal", equalTo(true))
        .body("[0].body", startsWith("SLA breached: was due"));
    }
    @Test(groups = "sla", description = "Customer does not see the SLA system note")
    public void customerDoesNotSeeSlaSystemNote()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/" + openTicketId + "/comments")

        .then()
        .statusCode(200)
        .body("authorRole", not(hasItem("SYSTEM")))
        .body("$", empty());
    }
    @Test(groups = "sla", description = "IN_PROGRESS ticket past due becomes breached")
    public void inProgressTicketPastDueBecomesBreached()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + inProgressTicketId)

        .then()
        .statusCode(200)
        .body("status", equalTo("IN_PROGRESS"))
        .body("sla.breached", equalTo(true))
        .body("sla.breachedAt", notNullValue());
    }
    @Test(groups = "sla", description = "WAITING_ON_CUSTOMER ticket is not flagged")
    public void waitingOnCustomerTicketIsNotFlagged()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + waitingTicketId)

        .then()
        .statusCode(200)
        .body("status", equalTo("WAITING_ON_CUSTOMER"))
        .body("sla.breached", equalTo(false))
        .body("sla.breachedAt", nullValue());
    }
    @Test(groups = "sla", description = "Ticket resolved before its due time is never flagged")
    public void ticketResolvedBeforeDueIsNeverFlagged()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + resolvedTicketId)

        .then()
        .statusCode(200)
        .body("status", equalTo("RESOLVED"))
        .body("sla.breached", equalTo(false));
    }
    @Test(groups = "sla", description = "Breach stays after priority is lowered")
    public void breachStaysAfterPriorityIsLowered()
    {
        Response response = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "LOW"))

        .when()
        .patch("/tickets/" + inProgressTicketId)

        .then()
        .statusCode(200)
        .body("priority", equalTo("LOW"))
        .body("sla.breached", equalTo(true))
        .extract().response();

        // The deadline moved to 72 hours after creation, but the ticket is still marked as late
        Instant createdAt = Instant.parse(response.path("createdAt"));
        Instant dueAt = Instant.parse(response.path("sla.dueAt"));
        Assert.assertEquals(Duration.between(createdAt, dueAt), Duration.ofHours(72));
    }
    @Test(groups = "sla", description = "Breach note is added only once")
    public void breachNoteIsAddedOnlyOnce()
    {
        // Let the background job run a few more times (it runs every 10 seconds), then count the notes
        await()
        .pollDelay(Duration.ofSeconds(25))
        .atMost(Duration.ofSeconds(40))
        .untilAsserted(() ->
            given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

            .when()
            .get("/tickets/" + openTicketId + "/comments")

            .then()
            .statusCode(200)
            .body("findAll { it.authorRole == 'SYSTEM' }", hasSize(1)));
    }
    @Test(groups = "sla", description = "Breached tickets show in filter and stats")
    public void breachedTicketsShowInFilterAndStats()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("breached", true)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.sla.breached", everyItem(equalTo(true)))
        .body("content.id", hasItem(openTicketId));

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .body("slaBreached", greaterThanOrEqualTo(1));
    }
    @Test(groups = "sla", description = "STS-005: Resolved breached tickets are not counted as breached")
    public void resolvedBreachedTicketsAreNotCountedAsBreached()
    {
        int before = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .when()
        .get("/tickets/stats")
        .then()
        .statusCode(200)
        .extract().path("slaBreached");

        // Resolve a breached ticket: OPEN -> IN_PROGRESS -> RESOLVED
        for (String status : List.of("IN_PROGRESS", "RESOLVED")) {
            given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
            .contentType("application/json")
            .body(Map.of("status", status))
            .when()
            .patch("/tickets/" + statsTicketId)
            .then()
            .statusCode(200);
        }

        // The ticket keeps its breached flag, but the dashboard no longer counts it
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/stats")

        .then()
        .statusCode(200)
        .body("slaBreached", equalTo(before - 1));
    }
}
