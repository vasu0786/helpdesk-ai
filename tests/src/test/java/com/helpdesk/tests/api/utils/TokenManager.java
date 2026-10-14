package com.helpdesk.tests.api.utils;

import com.helpdesk.tests.api.pojo.LoginRequest;
import java.util.HashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;

/** Logs in once per role and reuses the token, so tests do not repeat the login call */
public class TokenManager {

    private static final Map<String, String> tokens = new HashMap<>();

    /** role is "customer", "agent", "agent2" or "admin" - the prefixes used in config.properties */
    public static synchronized String getToken(String role) {
        if (!tokens.containsKey(role)) {
            LoginRequest user = new LoginRequest(ConfigReader.get(role + ".email"), ConfigReader.get(role + ".password"));
            String token = given()
                .contentType("application/json")
                .body(user)

                .when()
                .post("/auth/login")

                .then()
                .statusCode(200)
                .extract().path("token");
            tokens.put(role, token);
        }
        return tokens.get(role);
    }

    public static String customerToken() {
        return getToken("customer");
    }

    public static String agentToken() {
        return getToken("agent");
    }

    public static String adminToken() {
        return getToken("admin");
    }

    /** Forget the stored tokens, so the next call logs in again */
    public static synchronized void clear() {
        tokens.clear();
    }
}
