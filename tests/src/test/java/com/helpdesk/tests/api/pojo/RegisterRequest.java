package com.helpdesk.tests.api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Request body for POST /api/auth/register (always creates a CUSTOMER) */
@JsonInclude(JsonInclude.Include.NON_NULL) // fields left null are not sent at all
public class RegisterRequest {

    private String email;
    private String password;
    private String fullName;

    public RegisterRequest() {
    }

    public RegisterRequest(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
