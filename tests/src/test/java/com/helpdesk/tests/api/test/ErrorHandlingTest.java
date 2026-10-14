package com.helpdesk.tests.api.test;

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.TicketRequest;

import static io.restassured.RestAssured.*;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;

public class ErrorHandlingTest extends BaseApiTest {

    // ---------- ERR-001: every error has the standard format (schemas/error.json) ----------

    @Test(description = "400 error uses the standard error format")
    public void badRequestUsesStandardErrorFormat()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(Map.of("subject", "", "description", "API automation test"))

        .when()
        .post("/tickets")

        .then()
        .statusCode(400)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "401 error uses the standard error format")
    public void unauthorizedUsesStandardErrorFormat()
    {
        given()

        .when()
        .get("/tickets")

        .then()
        .statusCode(401)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "403 error uses the standard error format")
    public void forbiddenUsesStandardErrorFormat()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/users")

        .then()
        .statusCode(403)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "404 error uses the standard error format")
    public void notFoundUsesStandardErrorFormat()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets/999999999")

        .then()
        .statusCode(404)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "409 error uses the standard error format")
    public void conflictUsesStandardErrorFormat()
    {
        TicketRequest ticket = new TicketRequest("Error format", "API automation test");
        int id = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(ticket)

        .when()
        .post("/tickets")

        .then()
        .statusCode(201)
        .extract().path("id");

        // OPEN -> RESOLVED is not an allowed move
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("status", "RESOLVED"))

        .when()
        .patch("/tickets/" + id)

        .then()
        .statusCode(409)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }

    // ---------- ERR-002 to ERR-004: known defect BUG-004 ----------

    @Test(description = "BUG-004: Wrong Content-Type returns the standard error format")
    public void wrongContentTypeUsesStandardErrorFormat()
    {
        // Known defect BUG-004: the 415 body has no "message" and no "fieldErrors"
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("text/plain")
        .body("hello")

        .when()
        .post("/tickets")

        .then()
        .statusCode(415)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "BUG-004: Wrong HTTP method returns the standard error format")
    public void wrongHttpMethodUsesStandardErrorFormat()
    {
        // Known defect BUG-004: the 405 body has no "message" and no "fieldErrors"
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body("{}")

        .when()
        .put("/tickets/999999999")

        .then()
        .statusCode(405)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }
    @Test(description = "BUG-004: Unknown endpoint returns the standard error format")
    public void unknownEndpointUsesStandardErrorFormat()
    {
        // Known defect BUG-004: the 404 body for an unknown address has no "message" and no "fieldErrors"
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/nope")

        .then()
        .statusCode(404)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
    }

    // ---------- ERR-005: errors never leak internal details ----------

    @DataProvider(name = "requestsThatFail")
    public Object[][] requestsThatFail() {
        return new Object[][] {
            // what is wrong,              method,  path,                        body
            {"malformed JSON",             "POST",  "/tickets",                  "{ not json"},
            {"invalid enum in the body",   "POST",  "/tickets",                  "{\"subject\":\"a\",\"description\":\"b\",\"category\":\"XYZ\"}"},
            {"id is not a number",         "GET",   "/tickets/abc",              null},
            {"unknown ticket",             "GET",   "/tickets/999999999",        null},
            {"invalid filter value",       "GET",   "/tickets?status=INVALID",   null},
            {"invalid sort field",         "GET",   "/tickets?sort=nope,asc",    null}
        };
    }
    @Test(dataProvider = "requestsThatFail", description = "Errors never leak internal details")
    public void errorsNeverLeakInternalDetails(String what, String method, String path, String body)
    {
        String response;
        if (method.equals("POST")) {
            response = given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
            .contentType("application/json")
            .body(body)
            .when()
            .post(path)
            .asString();
        } else {
            response = given()
            .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
            .when()
            .get(path)
            .asString();
        }

        // No Java class or package names, stack traces or SQL in what the caller sees
        org.hamcrest.MatcherAssert.assertThat(what, response, allOf(
                not(containsString("java.")),
                not(containsString("com.helpdesk")),
                not(containsString("org.springframework")),
                not(containsString("Exception")),
                not(containsStringIgnoringCase("select ")),
                not(containsString("\tat "))));
    }

    // ---------- ERR-006 to ERR-008: general checks ----------

    @Test(description = "Health check is public")
    public void healthCheckIsPublic()
    {
        // No token is sent. The health check is not under /api, so the base path is switched off.
        given()
        .basePath("")

        .when()
        .get("/actuator/health")

        .then()
        .statusCode(200)
        .body("status", equalTo("UP"));
    }
    @Test(description = "Other management addresses are not public")
    public void otherManagementAddressesNeedAToken()
    {
        given()
        .basePath("")

        .when()
        .get("/actuator/env")

        .then()
        .statusCode(401);
    }
    @Test(description = "API documentation is available")
    public void apiDocumentationIsAvailable()
    {
        given()
        .basePath("")

        .when()
        .get("/v3/api-docs")

        .then()
        .statusCode(200)
        .body("openapi", notNullValue())
        .body("paths", hasKey("/api/tickets"))
        .body("paths", hasKey("/api/auth/login"));
    }
    @Test(description = "List response time is acceptable")
    public void listResponseTimeIsAcceptable()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/tickets")

        .then()
        .statusCode(200)
        .time(lessThan(1000L));      // milliseconds
    }
}
