package com.helpdesk.tests.api.pojo;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Request body for POST /api/auth/login */
@JsonInclude(JsonInclude.Include.NON_NULL) // fields left null are not sent at all
public class LoginRequest {

    private String email;
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
