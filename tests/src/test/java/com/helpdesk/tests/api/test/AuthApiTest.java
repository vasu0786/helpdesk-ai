package com.helpdesk.tests.api.test;
import io.restassured.response.Response;

import java.util.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.api.base.BaseApiTest;
import com.helpdesk.tests.api.pojo.LoginRequest;
import com.helpdesk.tests.api.pojo.RegisterRequest;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class AuthApiTest extends BaseApiTest {

@Test(description = "Login with valid customer credentials")
public void loginWithValidCustomer()
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("customer.email"),ConfigReader.get("customer.password"));
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("CUSTOMER"));

}  

@Test(description = "Login with valid Agent credentials")
public void loginWithValidAgent()
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("agent.email"),ConfigReader.get("agent.password"));
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("AGENT"));

}  
@Test(description = "Login with valid Admin credentials")
public void loginWithValidAdmin()
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("admin.email"),ConfigReader.get("admin.password"));
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("ADMIN"));

}  
@Test(description = "Login with Upper Case Email")
public void loginWithUpperCaseEmail()
{   String email= ConfigReader.get("customer.email").toUpperCase();
    LoginRequest validUser =new LoginRequest(email,ConfigReader.get("customer.password"));
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("CUSTOMER"))
    .body("user.email",equalTo("customer@helpdesk.local"));

}  
@Test(description = "Login with wrong password")
public void loginWithWrongPassword()
{   
    String pass="Test@123";
    LoginRequest validUser =new LoginRequest(ConfigReader.get("customer.email"),pass);
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(401)
    .body("message", equalTo("Invalid email or password"));


}  

@Test(description = "Login with unregistered email")
public void loginWithUnregisteredEmail()
{
   Map<String,Object> unRegistered = Map.of("email","customer1@helpdesk.ai","password","Customer@123");

    given()
    .contentType("application/json")
    .body(unRegistered)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(401)
    .body("message", equalTo("Invalid email or password"));
}  
@Test(description = "Login without email")
public void loginWithoutEmail()
{
   Map<String,Object> withoutEmail = Map.of("password","Customer@123");

    given()
    .contentType("application/json")
    .body(withoutEmail)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(400)
    .body("fieldErrors", hasKey("email"));
}  
@Test(description = "Login without Password")
public void loginWithoutPassword()
{
   Map<String,Object> withoutPass = Map.of("email","Customer@helpdesk.ai");

    given()
    .contentType("application/json")
    .body(withoutPass)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(400)
    .body("fieldErrors", hasKey("password"));
}  
@Test(description = "Login with malformed JSON returns 400")
public void loginWithMalformedJson() {
    String brokenJson = "{ \"email\": \"customer@helpdesk.local\", \"password\": ";   // cut off halfway

    given()
        .contentType("application/JSON")
        .body(brokenJson)
    .when()
        .post("/auth/login")
    .then()
        .statusCode(400)
        .body("message", equalTo("Malformed request"));
}
@Test(description = "Login with Deactivated User")
public void loginWithDeactivatedUser() {
    
    Map<String,Object> deactivated = Map.of("email","deactivate@g.com","password","Test@1234");
    given()
        .contentType("application/JSON")
        .body(deactivated)
    .when()
        .post("/auth/login")
    .then()
        .statusCode(401)
        .body("message", equalTo("Account is deactivated"));
}
@Test(description = "verify Response Does Not Have Pass")
public void verifyResponseDoesNotHavePass()
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("customer.email"),ConfigReader.get("customer.password"));
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("$",not(hasKey("password")));

}  
@Test(description = "Register a new customer")
public void registerNewCustomer()
{
    Map<String,Object>newUser = Map.of("email","user"+System.currentTimeMillis()+"@test.com","password","Test@1234","fullName","TestUser");

   
    given()
    .contentType("application/json")
    .body(newUser)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(201)
    .body("token",notNullValue());

}
@Test(description = "register With Already Existing Email")
public void registerWithAlreadyExistingEmail()
{
    RegisterRequest validUser =new RegisterRequest(ConfigReader.get("customer.email"),ConfigReader.get("customer.password"),"ExistngName");
    given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(409)
    .body("message",equalTo("A user with email "+ ConfigReader.get("customer.email") +" already exists"));

}
@DataProvider(name = "invalidEmails")
public Object[][] invalidEmails()
{
    return new Object[][] {
        { "plainaddress" },          // no @ at all
        { "user@" },                 // nothing after @
        { "@domain.com" },           // nothing before @
        { "user@@domain.com" },      // two @
        { "user name@domain.com" },  // space inside
        { "user@domain..com" },      // two dots in a row
        { "user.domain.com" }        // dot instead of @
    };
}
@Test(dataProvider = "invalidEmails", description = "Register with invalid email format")
public void registerWithInvalidEmailFormat(String invalidEmail)
{
    RegisterRequest invalidUser = new RegisterRequest(invalidEmail, "Test@1234", "TestUser");
    given()
    .contentType("application/json")
    .body(invalidUser)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors", hasKey("email"))
    .body("fieldErrors.email", equalTo("must be a well-formed email address"));

}
@Test(description = "Password with 7 characters is rejected")
public void registerWithSevenCharPassword()
{
    String sevenCharPassword = "Pass123";   // 7 characters, one below the minimum of 8
    RegisterRequest user = new RegisterRequest("user"+System.currentTimeMillis()+"@test.com", sevenCharPassword, "TestUser");
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors", hasKey("password"))
    .body("fieldErrors.password", equalTo("size must be between 8 and 72"))
    .body("token", nullValue());

}
@Test(description = "Password with 72 characters is accepted")
public void registerWithSeventyTwoCharPassword()
{
    String password72 = "P".repeat(72);   // exactly the maximum allowed length
    String email = "user"+System.currentTimeMillis()+"@test.com";
    RegisterRequest user = new RegisterRequest(email, password72, "TestUser");
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(201)
    .body("token", notNullValue())
    .body("user.email", equalTo(email));

}
@Test(description = "Password with 73 characters is rejected")
public void registerWithSeventyThreeCharPassword()
{
    String password73 = "P".repeat(73);   // one above the maximum allowed length
    RegisterRequest user = new RegisterRequest("user"+System.currentTimeMillis()+"@test.com", password73, "TestUser");
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors.password", equalTo("size must be between 8 and 72"))
    .body("token", nullValue());

}
@DataProvider(name = "blankNames")
public Object[][] blankNames()
{
    return new Object[][] {
        { "" },        // empty
        { "   " },     // only spaces
    };
}
@Test(dataProvider = "blankNames", description = "Full name blank or only spaces")
public void registerWithBlankFullName(String blankName)
{
    RegisterRequest user = new RegisterRequest("user"+System.currentTimeMillis()+"@test.com", "Test@1234", blankName);
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors.fullName", equalTo("must not be blank"))
    .body("token", nullValue());

}
@Test(description = "Full name with 120 characters is accepted")
public void registerWithOneTwentyCharFullName()
{
    String name120 = "N".repeat(120);   // exactly the maximum allowed length
    RegisterRequest user = new RegisterRequest("user"+System.currentTimeMillis()+"@test.com", "Test@1234", name120);
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(201)
    .body("token", notNullValue())
    .body("user.fullName", equalTo(name120));

}
@Test(description = "Full name with 121 characters is rejected")
public void registerWithOneTwentyOneCharFullName()
{
    String name121 = "N".repeat(121);   // one above the maximum allowed length
    RegisterRequest user = new RegisterRequest("user"+System.currentTimeMillis()+"@test.com", "Test@1234", name121);
    given()
    .contentType("application/json")
    .body(user)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(400)
    .body("message", equalTo("Validation failed"))
    .body("fieldErrors.fullName", equalTo("size must be between 0 and 120"))
    .body("token", nullValue());

}
@Test(description = "Cannot register as ADMIN by adding a role field")
public void registerCannotEscalateToAdmin()
{
    // Map, not the POJO: RegisterRequest has no "role" field, and this test must send one
    Map<String,Object> hacker = Map.of(
        "email", "hacker"+System.currentTimeMillis()+"@test.com",
        "password", "Test@1234",
        "fullName", "Hacker",
        "role", "ADMIN");

    String token =
    given()
    .contentType("application/json")
    .body(hacker)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(201)
    .body("user.role", equalTo("CUSTOMER"))   // role field must be ignored
    .extract().path("token");

    // Double-check the token really has no admin powers: listing users is staff-only
    given()
    .header("Authorization", "Bearer " + token)

    .when()
    .get("/users")

    .then()
    .statusCode(403);

}
@Test(description = "get Auth Me")
public void getAuthMe()
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("customer.email"),ConfigReader.get("customer.password"));
    String token = given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("CUSTOMER"))
    .extract().path("token");


    given()
    .header("Authorization", "Bearer " + token)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(200)
    .body("email", equalTo(ConfigReader.get("customer.email")))
    .body("role", equalTo("CUSTOMER"))
    .body("active", equalTo(true));

}
@Test(description = "Call /auth/me without a token")
public void getAuthMeWithoutToken()
{
    given()

    .when()
    .get("/auth/me")

    .then()
    .statusCode(401)
    .body("message", equalTo("Missing or invalid token"));

}
@Test(description = "Call /auth/me with a tampered token")
public void getAuthMeWithTamperedToken()
{
    LoginRequest validUser = new LoginRequest(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
    String token = given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .extract().path("token");

    // A JWT has 3 parts: header.payload.signature
    // Change the role inside the payload from CUSTOMER to ADMIN but keep the original signature,
    // exactly what an attacker would try. The server must notice the signature no longer matches.
    String[] parts = token.split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
    String forgedPayload = payload.replace("CUSTOMER", "ADMIN");
    String tamperedToken = parts[0] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes())
            + "." + parts[2];

    given()
    .header("Authorization", "Bearer " + tamperedToken)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(401)
    .body("message", equalTo("Missing or invalid token"));

}
@Test(description = "Call /auth/me with a token but without the Bearer prefix")
public void getAuthMeWithoutBearerPrefix()
{
    LoginRequest validUser = new LoginRequest(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
    String token = given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .extract().path("token");

    // The token itself is valid, but the header must look like "Bearer <token>".
    // Without the prefix the server should treat the request as having no token.
    given()
    .header("Authorization", token)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(401)
    .body("message", equalTo("Missing or invalid token"));

}

//Failing Because of the app built structure.
@Test(description = "Using expired token")
public void usingExpiredToken()throws InterruptedException
{
    LoginRequest validUser =new LoginRequest(ConfigReader.get("customer.email"),ConfigReader.get("customer.password"));
    String token = given()
    .contentType("application/json")
    .body(validUser)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .body("token", notNullValue())
    .body("user.role",equalTo("CUSTOMER"))
    .extract().path("token");

    Thread.sleep(125000);
    String message=given()
    .header("Authorization", "Bearer " + token)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(401)
   .extract().path("message");

System.out.println(message);
    
}
@Test(description = "BUG-001: Deactivated user's existing token stops working")
public void deactivatedUserTokenStopsWorking()
{
    // 1. Register a fresh user, so the demo customer is never deactivated
    RegisterRequest newUser = new RegisterRequest("deact" + System.currentTimeMillis() + "@test.com", "Test@1234", "Deact User");
    Response registerResponse = given()
    .contentType("application/json")
    .body(newUser)

    .when()
    .post("/auth/register")

    .then()
    .statusCode(201)
    .extract().response();

    String userToken = registerResponse.path("token");
    int userId = registerResponse.path("user.id");

    // 2. The token works while the user is active
    given()
    .header("Authorization", "Bearer " + userToken)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(200)
    .body("active", equalTo(true));

    // 3. Log in as admin
    LoginRequest admin = new LoginRequest(ConfigReader.get("admin.email"), ConfigReader.get("admin.password"));
    String adminToken = given()
    .contentType("application/json")
    .body(admin)

    .when()
    .post("/auth/login")

    .then()
    .statusCode(200)
    .extract().path("token");

    // 4. Admin deactivates the user
    given()
    .header("Authorization", "Bearer " + adminToken)
    .contentType("application/json")
    .body(Map.of("active", false))

    .when()
    .patch("/users/" + userId)

    .then()
    .statusCode(200)
    .body("active", equalTo(false));

    // 5. The old token must no longer work
    // Known defect BUG-001: the app currently returns 200 here
    given()
    .header("Authorization", "Bearer " + userToken)

    .when()
    .get("/auth/me")

    .then()
    .statusCode(401);

}

}
