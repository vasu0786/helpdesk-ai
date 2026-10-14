package com.helpdesk.tests.api.test;

import io.restassured.response.Response;

import java.time.Instant;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.api.utils.TokenManager;
import com.helpdesk.tests.api.base.BaseApiTest;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class UserApiTest extends BaseApiTest {
     @Test(description = "Admin can see all the users")
    public void adminSeesAllUsers()
    {
        given()
        .header("Authorization","Bearer "+TokenManager.getToken("admin"))
        .contentType("application/json")

        .when()
        .get("/users")

        .then()
        .statusCode(200)
        .body("$",not(empty()))
        .body("size()", greaterThan(0))
        .body("role", hasItems("ADMIN", "AGENT", "CUSTOMER"))
        .body("email", hasItem(ConfigReader.get("admin.email")));

}

    // ---------- helpers used by the tests below ----------

    /** A unique email, so a test never clashes with users from earlier runs. */
    private String newEmail()
    {
        return "user" + System.nanoTime() + "@test.com";
    }

    /** A complete, valid body for POST /users. Tests change one field of it to make it invalid. */
    private Map<String, Object> userBody(String email, String role)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", "Password1");
        body.put("fullName", "Test User");
        body.put("role", role);
        return body;
    }

    /** Admin creates a user with the given role and returns the new user's id. */
    private int createUser(String email, String role)
    {
        return given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(userBody(email, role))

        .when()
        .post("/users")

        .then()
        .statusCode(201)
        .extract().path("id");
    }

    // ---------- GET /users ----------

    @Test(description = "User list is newest first and never contains a password")
    public void userListIsNewestFirstWithoutPasswords()
    {
        List<String> created = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))

        .when()
        .get("/users")

        .then()
        .statusCode(200)
        .body("$", everyItem(hasKey("id")))
        .body("$", everyItem(hasKey("email")))
        .body("$", everyItem(hasKey("fullName")))
        .body("$", everyItem(hasKey("role")))
        .body("$", everyItem(hasKey("active")))
        .body("$", everyItem(not(hasKey("password"))))
        .body("$", everyItem(not(hasKey("passwordHash"))))
        .extract().path("createdAt");

        // Newest first: each user was created at or after the one below it
        for (int i = 0; i < created.size() - 1; i++) {
            Assert.assertFalse(Instant.parse(created.get(i)).isBefore(Instant.parse(created.get(i + 1))),
                    "Not newest first at position " + i);
        }
    }
    @Test(description = "Agent can list users (needed to assign tickets)")
    public void agentCanListUsers()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))

        .when()
        .get("/users")

        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("email", hasItem(ConfigReader.get("agent.email")));
    }
    @Test(description = "Customer cannot list users")
    public void customerCannotListUsers()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))

        .when()
        .get("/users")

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));
    }
    @Test(description = "Filter users by role")
    public void filterUsersByRole()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .queryParam("role", "AGENT")

        .when()
        .get("/users")

        .then()
        .statusCode(200)
        .body("$", not(empty()))
        .body("role", everyItem(equalTo("AGENT")))
        .body("email", hasItem(ConfigReader.get("agent.email")))
        .body("email", not(hasItem(ConfigReader.get("customer.email"))));
    }

    // ---------- POST /users ----------

    @Test(description = "Admin creates an agent")
    public void adminCreatesAgent()
    {
        String email = newEmail();

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(userBody(email, "AGENT"))

        .when()
        .post("/users")

        .then()
        .statusCode(201)
        .body("email", equalTo(email))
        .body("role", equalTo("AGENT"))
        .body("active", equalTo(true))
        .body("$", not(hasKey("password")));

        // The new agent can log in
        given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", "Password1"))

        .when()
        .post("/auth/login")

        .then()
        .statusCode(200)
        .body("user.role", equalTo("AGENT"));
    }
    @Test(description = "Admin creates another admin")
    public void adminCreatesAnotherAdmin()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(userBody(newEmail(), "ADMIN"))

        .when()
        .post("/users")

        .then()
        .statusCode(201)
        .body("role", equalTo("ADMIN"));
    }
    @Test(description = "Create user with an existing email")
    public void createUserWithExistingEmail()
    {
        String existing = ConfigReader.get("agent.email");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(userBody(existing, "AGENT"))

        .when()
        .post("/users")

        .then()
        .statusCode(409)
        .body("message", equalTo("A user with email " + existing + " already exists"));
    }
    @Test(description = "Create user with an invalid role")
    public void createUserWithInvalidRole()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(userBody(newEmail(), "BOSS"))

        .when()
        .post("/users")

        .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));
    }
    @DataProvider(name = "invalidUserFields")
    public Object[][] invalidUserFields() {
        return new Object[][] {
            // field,     value (null = field not sent),  expected error for that field
            {"email",    null,     "must not be blank"},
            {"email",    "nope",   "must be a well-formed email address"},
            {"password", "12345",  "size must be between 8 and 72"},
            {"role",     null,     "must not be null"}
        };
    }
    @Test(dataProvider = "invalidUserFields", description = "Create user with missing / invalid fields")
    public void createUserWithMissingOrInvalidField(String field, String value, String expectedError)
    {
        Map<String, Object> body = userBody(newEmail(), "AGENT");
        if (value == null) {
            body.remove(field);        // leave the field out
        } else {
            body.put(field, value);    // replace it with a bad value
        }

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(body)

        .when()
        .post("/users")

        .then()
        .statusCode(400)
        .body("message", equalTo("Validation failed"))
        .body("fieldErrors." + field, equalTo(expectedError));
    }
    @Test(description = "Agent cannot create users")
    public void agentCannotCreateUsers()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(userBody(newEmail(), "AGENT"))

        .when()
        .post("/users")

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));
    }
    @Test(description = "Customer cannot create users")
    public void customerCannotCreateUsers()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("customer"))
        .contentType("application/json")
        .body(userBody(newEmail(), "AGENT"))

        .when()
        .post("/users")

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));
    }

    // ---------- PATCH /users/{id} ----------

    @Test(description = "Admin deactivates an agent")
    public void adminDeactivatesAgent()
    {
        String email = newEmail();
        int id = createUser(email, "AGENT");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", false))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(200)
        .body("active", equalTo(false));

        // A deactivated user cannot log in
        given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", "Password1"))

        .when()
        .post("/auth/login")

        .then()
        .statusCode(401)
        .body("message", equalTo("Account is deactivated"));
    }
    @Test(description = "Admin re-activates a user")
    public void adminReactivatesUser()
    {
        String email = newEmail();
        int id = createUser(email, "AGENT");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", false))
        .when()
        .patch("/users/" + id)
        .then()
        .statusCode(200);

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", true))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(200)
        .body("active", equalTo(true));

        // The user can log in again
        given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", "Password1"))

        .when()
        .post("/auth/login")

        .then()
        .statusCode(200);
    }
    @Test(description = "Admin changes a user's role")
    public void adminChangesUserRole()
    {
        String email = newEmail();
        int id = createUser(email, "CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("role", "AGENT"))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(200)
        .body("role", equalTo("AGENT"));

        // The next login gives a token with the new role
        String token = given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", "Password1"))

        .when()
        .post("/auth/login")

        .then()
        .statusCode(200)
        .body("user.role", equalTo("AGENT"))
        .extract().path("token");

        // ...and that token can do agent things: listing users was 403 for a customer
        given()
        .header("Authorization", "Bearer " + token)

        .when()
        .get("/users")

        .then()
        .statusCode(200);
    }
    @Test(description = "Admin cannot change their own role")
    public void adminCannotChangeOwnRole()
    {
        int adminId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .when()
        .get("/auth/me")
        .then()
        .statusCode(200)
        .extract().path("id");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("role", "AGENT"))

        .when()
        .patch("/users/" + adminId)

        .then()
        .statusCode(400)
        .body("message", equalTo("You cannot change your own role or deactivate yourself"));
    }
    @Test(description = "Admin cannot deactivate themselves")
    public void adminCannotDeactivateThemselves()
    {
        int adminId = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .when()
        .get("/auth/me")
        .then()
        .statusCode(200)
        .extract().path("id");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", false))

        .when()
        .patch("/users/" + adminId)

        .then()
        .statusCode(400)
        .body("message", equalTo("You cannot change your own role or deactivate yourself"));
    }
    @Test(description = "Admin can change their own name")
    public void adminCanChangeOwnName()
    {
        Response me = given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .when()
        .get("/auth/me")
        .then()
        .statusCode(200)
        .extract().response();

        int adminId = me.path("id");
        String originalName = me.path("fullName");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("fullName", "Renamed Admin"))

        .when()
        .patch("/users/" + adminId)

        .then()
        .statusCode(200)
        .body("fullName", equalTo("Renamed Admin"))
        .body("role", equalTo("ADMIN"));

        // Put the original name back, so other runs see the same admin
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("fullName", originalName))

        .when()
        .patch("/users/" + adminId)

        .then()
        .statusCode(200)
        .body("fullName", equalTo(originalName));
    }
    @Test(description = "Update a user that does not exist")
    public void updateUserThatDoesNotExist()
    {
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("active", false))

        .when()
        .patch("/users/999999999")

        .then()
        .statusCode(404)
        .body("message", equalTo("User 999999999 not found"));
    }
    @Test(description = "Agent cannot update users")
    public void agentCannotUpdateUsers()
    {
        String email = newEmail();
        int id = createUser(email, "CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("agent"))
        .contentType("application/json")
        .body(Map.of("active", false))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(403)
        .body("message", equalTo("Access denied"));

        // The user was not deactivated: they can still log in
        given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", "Password1"))

        .when()
        .post("/auth/login")

        .then()
        .statusCode(200);
    }
    @Test(description = "Full name cannot be empty")
    public void fullNameCannotBeEmpty()
    {
        int id = createUser(newEmail(), "CUSTOMER");

        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("fullName", ""))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(400)
        .body("fieldErrors.fullName", equalTo("size must be between 1 and 120"));
    }
    @Test(description = "BUG-003: Full name cannot be only spaces")
    public void fullNameCannotBeOnlySpaces()
    {
        int id = createUser(newEmail(), "CUSTOMER");

        // Known defect BUG-003: the app currently returns 200 and saves an empty name
        given()
        .header("Authorization", "Bearer " + TokenManager.getToken("admin"))
        .contentType("application/json")
        .body(Map.of("fullName", "   "))

        .when()
        .patch("/users/" + id)

        .then()
        .statusCode(400);
    }
}
