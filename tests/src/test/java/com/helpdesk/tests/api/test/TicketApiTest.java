package com.helpdesk.tests.api.test;
import io.restassured.response.Response;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import javax.mail.internet.HeaderTokenizer.Token;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.api.utils.DbUtils;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.LoginRequest;
import com.helpdesk.tests.api.pojo.RegisterRequest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
public class TicketApiTest  extends BaseApiTest{
    @Test(description = "Customer raises a valid ticket")
    public void customerRaisesAvalidTicket()
    {
        
   
        TicketRequest ticket = new TicketRequest("Test", "API automation test");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("status", equalTo("OPEN"))
        .body("category", equalTo(null));


    }
    @Test(description = "Raise ticket with a category")
    public void raiseTicketWithCategory()
    {

        TicketRequest ticket = new TicketRequest("Charged twice for one order", "API automation test");
        ticket.setCategory("BILLING");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("status", equalTo("OPEN"))
        .body("category", equalTo("BILLING"));


    }
    @Test(description = "SLA due time of a new ticket is 24h after creation")
    public void newTicketSlaDueTimeIs24HoursAfterCreation()
    {

        TicketRequest ticket = new TicketRequest("Unable to download invoice", "API automation test");
        Response response = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("priority", equalTo("MEDIUM"))
        .body("sla.breached", equalTo(false))
        .extract().response();

        // Compare the two server timestamps with each other, not with this machine's clock
        Instant createdAt = Instant.parse(response.path("createdAt"));
        Instant dueAt = Instant.parse(response.path("sla.dueAt"));
        Assert.assertEquals(Duration.between(createdAt, dueAt), Duration.ofHours(24));

    }
    @Test(description = "Customer cannot raise a ticket on someone else's behalf")
    public void customerCannotRaiseTicketOnSomeoneElseBehave()
    {

        String someoneElse = ConfigReader.get("agent.email");
        TicketRequest ticket = new TicketRequest("Raised with another person's email", "API automation test");
        ticket.setCustomerEmail(someoneElse);
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        // The email sent in the body is ignored: the ticket belongs to the logged-in customer
        .body("customerEmail", equalTo(ConfigReader.get("customer.email")))
        .body("customerEmail", not(equalTo(someoneElse)));


    }
    @Test(description = "Agent raises a ticket for a customer")
    public void agentRaisesTicketForCustomer()
    {

        TicketRequest ticket = new TicketRequest("Customer called about a late refund", "API automation test");
        ticket.setCustomerEmail(ConfigReader.get("customer.email"));
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("status", equalTo("OPEN"))
        .body("customerEmail", equalTo(ConfigReader.get("customer.email")));


    }
    @Test(description = "Agent raises a ticket without customerEmail")
    public void agentRaisesATicketWithoutEmail()
    {
        TicketRequest ticket = new TicketRequest("Test Email","Test Create ticket without Email");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("customerEmail is required when staff create a ticket"));

    }
   @DataProvider(name = "invalidCustomerEmails")
public Object[][] invalidCustomerEmails() {
    return new Object[][] {
        {"not-an-email"},
        {"abc@"},
        {"@test.com"}
    };
}

@Test(dataProvider = "invalidCustomerEmails", description = "Agent sends invalid customerEmail")
public void agentSendsInvalidCustomerEmail(String invalidEmail)
{
    // use invalidEmail here, e.g. ticket.setCustomerEmail(invalidEmail);
    TicketRequest ticket = new TicketRequest("Test invalid ", "Test Invalid Email id");
    ticket.setCategory("BILLING");
    ticket.setCustomerEmail(invalidEmail);
    given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
    .contentType("application/json")
    .body(ticket)

    .when()
    .post("/tickets")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors.customerEmail", equalTo("must be a well-formed email address"));


}

    @Test(description = "Raise ticket without a subject")
    public void raiseTicketWithoutSubject()
    {

        TicketRequest ticket = new TicketRequest();
        ticket.setDescription("API automation test");
        ticket.setCategory("BILLING");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("fieldErrors",hasKey("subject"));


    }
    @Test(description = "Raise ticket with an empty subject")
    public void raiseTicketWithEmptySubject()
    {

        TicketRequest ticket = new TicketRequest("", "API automation test");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors.subject", equalTo("must not be blank"));


    }
    @Test(description = "Subject with 200 characters is accepted")
    public void raiseTicketWithTwoHundredCharSubject()
    {

        String subject200 = "S".repeat(200);
        TicketRequest ticket = new TicketRequest(subject200, "API automation test");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("subject", equalTo(subject200));


    }
    @Test(description = "Subject with 201 characters is rejected")
    public void raiseTicketWithTwoHundredOneCharSubject()
    {

        String subject201 = "S".repeat(201);
        TicketRequest ticket = new TicketRequest(subject201, "API automation test");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors.subject", equalTo("size must be between 0 and 200"));


    }
    @DataProvider(name = "blankDescriptions")
    public Object[][] blankDescriptions() {
        return new Object[][] {
            {null},     // field not sent at all
            {""},       // empty
            {"   "}     // only spaces
        };
    }
    @Test(dataProvider = "blankDescriptions", description = "Description missing or blank")
    public void raiseTicketWithBlankDescription(String description)
    {

        TicketRequest ticket = new TicketRequest("Description check", description);
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors.description", equalTo("must not be blank"));


    }
    @Test(description = "Description with 5000 characters is accepted")
    public void raiseTicketWithFiveThousandCharDescription()
    {

        String description5000 = "D".repeat(5000);
        TicketRequest ticket = new TicketRequest("Long description check", description5000);
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("description", equalTo(description5000));


    }
    @Test(description = "Description with 5001 characters is rejected")
    public void raiseTicketWithFiveThousandOneCharDescription()
    {

        String description5001 = "D".repeat(5001);
        TicketRequest ticket = new TicketRequest("Long description check", description5001);
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors.description", equalTo("size must be between 0 and 5000"));


    }
    @DataProvider(name = "invalidCategories")
    public Object[][] invalidCategories() {
        return new Object[][] {
            {"INVALID"},    // not one of the five categories
            {"Billing"},    // right word, wrong case
            {""}            // empty
        };
    }
    @Test(dataProvider = "invalidCategories", description = "Invalid category value")
    public void raiseTicketWithInvalidCategory(String category)
    {

        TicketRequest ticket = new TicketRequest("Category check", "API automation test");
        ticket.setCategory(category);
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));


    }
    @Test(description = "Leading and trailing spaces in subject and description are trimmed")
    public void trimmedSubject()
    {

        TicketRequest ticket = new TicketRequest("   Subject check      ", "   Description check   ");
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("subject", equalTo("Subject check"))
        .body("description", equalTo("Description check"));


    }
    @Test(description = "Raise ticket without a token")
    public void raiseTicketWithoutToken()
    {

        TicketRequest ticket = new TicketRequest("No token check", "API automation test");
        given()
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(401)
        .body("message", equalTo("Missing or invalid token"));


    }
    @Test(description = "Ticket created through the API is stored correctly in the DB")
    public void createdTicketIsStoredInDb()
    {
        String subject = "Stored in DB check " + System.currentTimeMillis();
        String description = "API automation test";

        // 1. Create the ticket through the API
        TicketRequest ticket = new TicketRequest(subject, description);
        int ticketId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 2. Read the same ticket straight from the database
        Map<String, Object> row = DbUtils.queryOne("SELECT * FROM tickets WHERE id = ?", ticketId);

        // 3. Compare the DB row with what was sent
        Assert.assertNotNull(row, "Ticket " + ticketId + " not found in DB");
        Assert.assertEquals(row.get("subject"), subject);
        Assert.assertEquals(row.get("description"), description);
        Assert.assertEquals(row.get("customer_email"), ConfigReader.get("customer.email"));
        Assert.assertEquals(row.get("status"), "OPEN");
        Assert.assertEquals(row.get("priority"), "MEDIUM");
        Assert.assertNull(row.get("category"));
        Assert.assertNull(row.get("assignee_id"));
        Assert.assertEquals(row.get("sla_breached"), false);

        // SLA due time is stored as created_at + 24 hours
        Timestamp createdAt = (Timestamp) row.get("created_at");
        Timestamp slaDueAt = (Timestamp) row.get("sla_due_at");
        Assert.assertEquals(Duration.between(createdAt.toInstant(), slaDueAt.toInstant()), Duration.ofHours(24));
    }
    @Test(description = "Customer views own ticket")
    public void customerViewsOwnTicket()
    {
        TicketRequest ticket = new TicketRequest("Get a ticket by id","Get a ticket by id frm customer token");
        int id = given()
        .header("Authorization","Bearer "+TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");   
        
        given()
        .header("Authorization","Bearer "+TokenManager.getToken("customer"))

        .when().get("/tickets/"+id)

        .then().statusCode(200)
        .body("id", equalTo(id));
    }
    @Test(description = "Customer cannot view another customer's ticket")
    public void customerCannotViewAnotherCustomersTicket()
    {
        
   
        TicketRequest ticket = new TicketRequest("Test", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

       // Customer B: a brand-new customer, registered just for this test
        RegisterRequest otherCustomer = new RegisterRequest("other" + System.currentTimeMillis() + "@test.com", "Test@1234", "Other Customer");
        String otherToken = given()
        .contentType("application/json")
        .body(otherCustomer)

        .when()
        .post("/auth/register")

        .then()
        .statusCode(201)
        .extract().path("token");


        given()
        .header("Authorization","Bearer "+otherToken)

        .when()
        .get("/tickets/"+id)

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket " +id+" not found"));
    }
    @Test(description = "Agent views any customer's ticket")
    public void agentViewsAnyCustomersTicket()
    {
        String subject = "Agent can view this ticket";
        TicketRequest ticket = new TicketRequest(subject, "API automation test");
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

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("id", equalTo(id))
        .body("subject", equalTo(subject))
        .body("customerEmail", equalTo(ConfigReader.get("customer.email")));
    }
    @Test(description = "Ticket id does not exist")
    public void viewTicketThatDoesNotExist()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/999999999")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket 999999999 not found"));
    }
    @Test(description = "Ticket id is not a number")
    public void viewTicketWithNonNumericId()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/abc")

        .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));
    }
    @Test(description = "Customer's list contains only their own tickets")
    public void customerListContainsOnlyOwnTickets()
    {
        // 1. Customer A creates a ticket
        TicketRequest ownTicket = new TicketRequest("Customer A ticket", "API automation test");
        int ownId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ownTicket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 2. Register Customer B
        RegisterRequest otherCustomer = new RegisterRequest("other" + System.currentTimeMillis() + "@test.com", "Test@1234", "Other Customer");
        String otherToken = given()
        .contentType("application/json")
        .body(otherCustomer)

        .when()
        .post("/auth/register")

        .then()
        .statusCode(201)
        .extract().path("token");

        // 3. Customer B creates a ticket
        TicketRequest otherTicket = new TicketRequest("Customer B ticket", "API automation test");
        int otherId = given()
        .header("Authorization", "Bearer " + otherToken)
        .contentType("application/json")
        .body(otherTicket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 4. Customer A's list shows only A's tickets
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.customerEmail", everyItem(equalTo(ConfigReader.get("customer.email"))))
        .body("content.id", hasItem(ownId))
        .body("content.id", not(hasItem(otherId)));
    }
    @Test(description = "Default paging and sorting")
public void defaultPagingAndSorting()
{
    List<String> dates = given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

    .when()
    .get("/tickets")

    .then()
    .statusCode(200)
    .body("page.totalElements", greaterThan(20))
    .body("page.number", equalTo(0))
    .body("page.size", equalTo(20))
    .body("content", hasSize(20))
    .extract().path("content.createdAt");

    // Newest first: each ticket must not be older than the one after it
    for (int i = 0; i < dates.size() - 1; i++) {
        Instant current = Instant.parse(dates.get(i));
        Instant next = Instant.parse(dates.get(i + 1));
        Assert.assertFalse(current.isBefore(next), "Not newest first at position " + i);
    }
}

@Test(description = "Filter by Status")
public void filterByStatus()
{
     given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

    .when()
    .get("/tickets?status=OPEN")

    .then()
    .statusCode(200)
    .body("content.status",everyItem(equalTo("OPEN")));
    

    // // Newest first: each ticket must not be older than the one after it
    // for (int i = 0; i < status.size() - 1; i++) {
        
    //     Assert.assertEquals(status.get(i),"OPEN");
    // }
}
    @Test(description = "Filter by priority")
    public void filterByPriority()
    {
        // A new ticket is MEDIUM, so an agent raises this one to HIGH first
        TicketRequest ticket = new TicketRequest("Filter by priority", "API automation test");
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
        .body(Map.of("priority", "HIGH"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("priority", "HIGH")

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.priority", everyItem(equalTo("HIGH")))
        .body("content.id", hasItem(id));
    }
    @Test(description = "Filter by category")
    public void filterByCategory()
    {
        TicketRequest ticket = new TicketRequest("Filter by category", "API automation test");
        ticket.setCategory("BILLING");
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
        .queryParam("category", "BILLING")

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.category", everyItem(equalTo("BILLING")))
        .body("content.id", hasItem(id));
    }
    @Test(description = "Filter by assignee")
    public void filterByAssignee()
    {
        // The agent's own user id, needed for assigning and for the filter
        int agentId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        TicketRequest ticket = new TicketRequest("Filter by assignee", "API automation test");
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
        .body(Map.of("assigneeId", agentId))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("assignee.id", equalTo(agentId));

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("assigneeId", agentId)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.assignee.id", everyItem(equalTo(agentId)))
        .body("content.id", hasItem(id));
    }
    @Test(description = "Filter unassigned tickets")
    public void filterUnassignedTickets()
    {
        // A new ticket has no assignee
        TicketRequest ticket = new TicketRequest("Filter unassigned", "API automation test");
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
        .queryParam("unassigned", true)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.assignee", everyItem(nullValue()))
        .body("content.id", hasItem(id));
    }
    @Test(description = "Filter breached tickets")
    public void filterBreachedTickets()
    {
        // A ticket created just now cannot have missed its 24h deadline
        TicketRequest ticket = new TicketRequest("Filter breached", "API automation test");
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
        .queryParam("breached", true)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.sla.breached", everyItem(equalTo(true)))
        .body("content.id", not(hasItem(id)));

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("breached", false)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content.sla.breached", everyItem(equalTo(false)))
        .body("content.id", hasItem(id));
    }
    @Test(description = "Search partial subject name")
    public void searchPartialSubName()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?q=check")

        .then()
        .statusCode(200)
        .body("content.subject",everyItem(containsStringIgnoringCase("check")));
        

        // // Newest first: each ticket must not be older than the one after it
        // for (int i = 0; i < status.size() - 1; i++) {
            
        //     Assert.assertEquals(status.get(i),"OPEN");
        // }
    }
    @Test(description = "Search by customer email")
    public void searchByCustomerEmail()
    {
        // An agent raises a ticket for a unique email, so only this ticket can match the search
        String email = "search" + System.currentTimeMillis() + "@test.com";
        TicketRequest ticket = new TicketRequest("Refund not received", "API automation test");
        ticket.setCustomerEmail(email);
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("q", email)

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .body("content", hasSize(1))
        .body("content[0].id", equalTo(id))
        .body("content[0].customerEmail", equalTo(email));
    }
    @Test(description = "Combined filters work together (AND)")
    public void combinedFilter()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?status=OPEN&priority=MEDIUM")

        .then()
        .statusCode(200)
        .body("content.status",everyItem(equalTo("OPEN")))
        .body("content.priority",everyItem(equalTo("MEDIUM")));
        

        // // Newest first: each ticket must not be older than the one after it
        // for (int i = 0; i < status.size() - 1; i++) {
            
        //     Assert.assertEquals(status.get(i),"OPEN");
        // }
         }
    @DataProvider(name = "invalidFilters")
    public Object[][] invalidFilters() {
        return new Object[][] {
            {"status", "INVALID"},      // not a real status
            {"status", "open"},         // right word, wrong case
            {"priority", "SUPER"},
            {"category", "XYZ"},
            {"assigneeId", "abc"},      // must be a number
            {"unassigned", "maybe"},    // must be true or false
            {"breached", "yesno"}
        };
    }
    @Test(dataProvider = "invalidFilters", description = "Invalid filter value")
    public void invalidFilterValue(String filter, String value)
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam(filter, value)

        .when()
        .get("/tickets")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors", hasKey(filter));
    }
    @Test(description = "Second page of results")
public void secondPageOfResults()
{
    List<Integer> firstPageIds = given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

    .when()
    .get("/tickets?page=0&size=5")

    .then()
    .statusCode(200)
    .extract().path("content.id");

    List<Integer> secondPageIds = given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

    .when()
    .get("/tickets?page=1&size=5")

    .then()
    .statusCode(200)
    .body("page.number", equalTo(1))
    .body("page.size", equalTo(5))
    .body("content", hasSize(5))
    .extract().path("content.id");

    // No ticket may appear on both pages
    for (Integer id : secondPageIds) {
        Assert.assertFalse(firstPageIds.contains(id), "Ticket " + id + " is on both pages");
    }
}
    @Test(description = "Page size is capped at 2000")
    public void pageSizeIsCappedAtTwoThousand()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?size=2000")

        .then()
        .statusCode(200)
        .body("page.size", equalTo(2000));

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?size=2001")

        .then()
        .statusCode(200)
        .body("page.size", equalTo(2000));
    }
    @Test(description = "Sort by a valid field")
    public void sortByValidField()
    {
        List<Integer> ids = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?sort=id,asc")

        .then()
        .statusCode(200)
        .body("content", not(empty()))
        .extract().path("content.id");

        // Ascending: each id must be smaller than the one after it
        for (int i = 0; i < ids.size() - 1; i++) {
            Assert.assertTrue(ids.get(i) < ids.get(i + 1), "Not ascending at position " + i);
        }
    }
    @Test(description = "Sort by an unknown field")
    public void sortByUnknownField()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets?sort=doesNotExist,asc")

        .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));
    }
    @DataProvider(name = "sqlInjectionTexts")
    public Object[][] sqlInjectionTexts() {
    return new Object[][] {
        {"' OR '1'='1"},                  // tries to make the condition always true
        {"'; DROP TABLE tickets; --"},    // tries to run a second command
        {"%"},                            // SQL wildcard: any text
        {"_"}                             // SQL wildcard: any one character
    };
}
@Test(dataProvider = "sqlInjectionTexts", description = "SQL injection text in search is harmless")
public void sqlInjectionTextInSearchIsHarmless(String text)
{
    // The text must be searched as plain text: no error and no tickets matched
    given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
    .queryParam("q", text)

    .when()
    .get("/tickets")

    .then()
    .statusCode(200)
    .body("content", empty());

    // The tickets are all still there
    given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

    .when()
    .get("/tickets")

    .then()
    .statusCode(200)
    .body("page.totalElements", greaterThan(0));
}
    @DataProvider(name = "assigneeId")
    public Object[][] assignee() {
    return new Object[][] {
        {"unassigned=true"},                  // tries to make the condition always true
        {"assigneeId=2"},    // tries to run a second command                            // SQL wildcard: any text
        {"q=someone.else@test.com" } 
    };
}        
    @Test(dataProvider = "assigneeId",description= "Customer using staff filters still sees only own tickets")
    public void customerSeesOnlyHisTicket(String assignee)
    {
        // A ticket that belongs to someone else: the agent raises it for another email
        TicketRequest otherTicket = new TicketRequest("Belongs to someone else", "API automation test");
        otherTicket.setCustomerEmail("someone.else@test.com");
        int otherId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(otherTicket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        given()
        .header("Authorization","Bearer "+TokenManager.getToken("customer"))

        .when()
        .get("/tickets?"+assignee)

        .then()
        .body("content.customerEmail", everyItem(equalTo(ConfigReader.get("customer.email"))))
        .body("content.id",not(hasItem(otherId)));
    }

    @Test(description = "SLA due time follows the priority")
    public void slaDueTimeFollowsThePriority()
{
    // 1. Customer creates a ticket (MEDIUM, due in 24 hours)
        TicketRequest ticket = new TicketRequest("SLA follows priority", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 2. Agent raises it to URGENT
        Response response = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "URGENT"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("priority", equalTo("URGENT"))
        .extract().response();

        // 3. The deadline is now 4 hours after creation
        Instant createdAt = Instant.parse(response.path("createdAt"));
        Instant dueAt = Instant.parse(response.path("sla.dueAt"));
        Assert.assertEquals(Duration.between(createdAt, dueAt), Duration.ofHours(4));
}
    @Test(description = "Agent updates the category of a ticket")
    public void agentUpdatesCategory()
    {
        // 1. Customer creates a ticket with category BUG
        String subject = "Update category";
        TicketRequest ticket = new TicketRequest(subject, "API automation test");
        ticket.setCategory("BUG");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("category", equalTo("BUG"))
        .extract().path("id");

        // 2. Agent changes it to BILLING: only the category changes
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("category", "BILLING"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("category", equalTo("BILLING"))
        .body("subject", equalTo(subject))
        .body("priority", equalTo("MEDIUM"));
    }
    @Test(description = "Agent assigns a ticket to an agent")
    public void agentAssignsTicket()
    {
        // The agent's own user id, used as the assignee
        int agentId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        // 1. Customer creates a ticket: it has no assignee yet
        TicketRequest ticket = new TicketRequest("Assign this ticket", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .body("assignee", nullValue())
        .extract().path("id");

        // 2. Agent assigns it
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("assigneeId", agentId))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("assignee.id", equalTo(agentId))
        .body("assignee.email", equalTo(ConfigReader.get("agent.email")));
    }
    @Test(description = "Assign to an admin")
    public void agentAssignsTicketToAdmin()
    {
        // The admin's user id, used as the assignee
        int adminId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        TicketRequest ticket = new TicketRequest("Assign this ticket to an admin", "API automation test");
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
        .body(Map.of("assigneeId", adminId))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("assignee.id", equalTo(adminId))
        .body("assignee.email", equalTo(ConfigReader.get("admin.email")));
    }
    @Test(description = "Assign to a customer")
    public void assignTicketToCustomer()
    {
        // The customer's user id: a customer must not be able to hold tickets
        int customerId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        TicketRequest ticket = new TicketRequest("Assign this ticket to a customer", "API automation test");
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
        .body(Map.of("assigneeId", customerId))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(400)
        .body("message", equalTo("Tickets can only be assigned to active agents or admins"));
    }
    @Test(description = "Assign to a deactivated agent")
    public void assignTicketToDeactivatedAgent()
    {
        // 1. Admin creates a new agent, then deactivates them
        int agentId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("email", "agent" + System.currentTimeMillis() + "@test.com",
                     "password", "Test@1234", "fullName", "Deactivated Agent", "role", "AGENT"))

        .when()
        .post("/users")

        .then()
        .statusCode(201)
        .extract().path("id");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", false))

        .when()
        .patch("/users/" + agentId)

        .then()
        .statusCode(200)
        .body("active", equalTo(false));

        // 2. Customer creates a ticket
        TicketRequest ticket = new TicketRequest("Assign this ticket to a deactivated agent", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 3. Assigning it to the deactivated agent is refused
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("assigneeId", agentId))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(400)
        .body("message", equalTo("Tickets can only be assigned to active agents or admins"));
    }
    @Test(description = "Assign to a user id that does not exist")
    public void assignTicketToUserThatDoesNotExist()
    {
        TicketRequest ticket = new TicketRequest("Assign this ticket to nobody", "API automation test");
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
        .body(Map.of("assigneeId", 999999999))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(400)
        .body("message", equalTo("Assignee 999999999 does not exist"));
    }
    @Test(description = "Uassign a ticket")
    public void unassignATicket()
    {
        int agentId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");


        TicketRequest ticket = new TicketRequest("subject unassign","unassign a ticket ");
        int id =
        given()
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
        .body(Map.of("assigneeId",agentId))

        .when()
        .patch("/tickets/"+id)

        .then()
        .statusCode(200)
        .body("assignee.id", equalTo(agentId));


        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("unassign", true))

        .when()
        .patch("/tickets/"+id)

        .then()
        .statusCode(200)
        .body("assignee", nullValue());

    }
    @Test(description = "Send assigneeId and unassign together")
    public void sendAssigneeIdAndUnassignTogether()
    {
        int agentId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/auth/me")

        .then()
        .statusCode(200)
        .extract().path("id");

        TicketRequest ticket = new TicketRequest("Assign and unassign together", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // The two instructions contradict each other, so the request is rejected
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("assigneeId", agentId, "unassign", true))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(400)
        .body("message", equalTo("Send either assigneeId or unassign, not both"));

        // Nothing was changed: the ticket is still unassigned
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("assignee", nullValue());
    }
    @Test(description = "Empty update body changes nothing")
    public void emptyUpdateBodyChangesNothing()
    {
        String subject = "Empty update";
        TicketRequest ticket = new TicketRequest(subject, "API automation test");
        ticket.setCategory("BUG");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // An update with no fields is accepted and every value stays as it was
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body("{}")

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("subject", equalTo(subject))
        .body("status", equalTo("OPEN"))
        .body("category", equalTo("BUG"))
        .body("priority", equalTo("MEDIUM"))
        .body("assignee", nullValue());
    }
    @DataProvider(name = "invalidPriorities")
    public Object[][] invalidPriorities() {
        return new Object[][] {
            {"SUPER"},      // not one of the four priorities
            {"high"},       // right word, wrong case
            {""}            // empty
        };
    }
    @Test(dataProvider = "invalidPriorities", description = "Invalid priority value")
    public void updateWithInvalidPriority(String priority)
    {
        TicketRequest ticket = new TicketRequest("Invalid priority", "API automation test");
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
        .body(Map.of("priority", priority))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));
    }
    @Test(description = "Customer cannot update a ticket")
    public void customerCannotUpdateTicket()
    {
        TicketRequest ticket = new TicketRequest("Customer tries to update", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // The customer owns the ticket but is still not allowed to change it
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("priority", "URGENT"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));

        // The priority was not changed
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("priority", equalTo("MEDIUM"));
    }
        @Test(description = "Update without a token")
    public void updateWithoutToken()
    {
        given()
        .contentType("application/json")
        .body(Map.of("priority", "HIGH"))

        .when()
        .patch("/tickets/999999999")

        .then()
        .statusCode(401)
        .body("message", equalTo("Missing or invalid token"));
    }
    @Test(description = "Update a ticket that does not exist")
    public void updateTicketThatDoesNotExist()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "HIGH"))

        .when()
        .patch("/tickets/999999999")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket 999999999 not found"));
    }
    @Test(description = "Unsupported HTTP method")
public void unsupportedHttpMethod()
{
    given()
    .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
    .contentType("application/json")
    .body(Map.of("priority", "HIGH"))

    .when()
    .put("/tickets/999999999")

    .then()
    .statusCode(405)
    .header("Allow", containsString("PATCH"));
}
    @Test(description = "BUG-002: A CLOSED ticket cannot be edited")
    public void closedTicketCannotBeEdited()
    {
        TicketRequest ticket = new TicketRequest("Edit after closing", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 1. Agent closes the ticket
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "CLOSED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(200)
        .body("status", equalTo("CLOSED"));

        // 2. Any further edit must be refused: CLOSED is final
        // Known defect BUG-002: the app currently returns 200 and changes the priority
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("priority", "URGENT"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(409);
    }

        @Test(description = "Admin deletes a ticket")
        public void adminDeletesTicket()
    {
        TicketRequest ticket = new TicketRequest("Delete this ticket", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // your delete step goes here, using id

        given()
        .header("Authorization","Bearer "+TokenManager.getToken("admin"))
       

        .when()
        .delete("/tickets/"+id)

        .then()
        .statusCode(204);

    }
    @Test(description = "DB check: comments are deleted with the ticket")
    public void commentsAreDeletedWithTheTicket()
    {
        // 1. Customer creates a ticket
        TicketRequest ticket = new TicketRequest("Delete ticket with comments", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // 2. Two comments are added to it
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("body", "First comment"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("body", "Second comment"))
        .when()
        .post("/tickets/" + id + "/comments")
        .then()
        .statusCode(201);

        // 3. Both comments are in the database
        Assert.assertEquals(DbUtils.count("SELECT COUNT(*) FROM comments WHERE ticket_id = ?", id), 2);

        // 4. Admin deletes the ticket
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .delete("/tickets/" + id)

        .then()
        .statusCode(204);

        // 5. The ticket row and its comments are gone
        Assert.assertEquals(DbUtils.count("SELECT COUNT(*) FROM tickets WHERE id = ?", id), 0);
        Assert.assertEquals(DbUtils.count("SELECT COUNT(*) FROM comments WHERE ticket_id = ?", id), 0);
    }
    @Test(description = "Agent cannot delete")
    public void agentCannotDeleteTicket()
    {
        TicketRequest ticket = new TicketRequest("Agent tries to delete", "API automation test");
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

        .when()
        .delete("/tickets/" + id)

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));

        // The ticket is still there
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200);
    }
    @Test(description = "Customer cannot delete own ticket")
    public void customerCannotDeleteOwnTicket()
    {
        TicketRequest ticket = new TicketRequest("Customer tries to delete", "API automation test");
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
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .delete("/tickets/" + id)

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));

        // The ticket is still there
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/tickets/" + id)

        .then()
        .statusCode(200);
    }
    @Test(description = "Delete a ticket that does not exist")
    public void deleteTicketThatDoesNotExist()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .delete("/tickets/999999999")

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket 999999999 not found"));
    }
    @Test(description = "Delete the same ticket twice")
    public void deleteSameTicketTwice()
    {
        TicketRequest ticket = new TicketRequest("Delete twice", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // First delete works
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .delete("/tickets/" + id)

        .then()
        .statusCode(204);

        // Second delete finds nothing
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .delete("/tickets/" + id)

        .then()
        .statusCode(404)
        .body("message", equalTo("Ticket " + id + " not found"));
    }
}
