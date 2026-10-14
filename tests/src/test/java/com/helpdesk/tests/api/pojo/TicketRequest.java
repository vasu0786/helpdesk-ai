package com.helpdesk.tests.api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Request body for POST /api/tickets */
@JsonInclude(JsonInclude.Include.NON_NULL) // fields left null are not sent at all
public class TicketRequest {

    private String subject;
    private String description;
    private String category;      // optional
    private String customerEmail; // only needed when an agent/admin creates a ticket for a customer

    public TicketRequest() {
    }

    public TicketRequest(String subject, String description) {
        this.subject = subject;
        this.description = description;
    }

    public TicketRequest(String subject, String description, String category, String customerEmail) {
        this.subject = subject;
        this.description = description;
        this.category = category;
        this.customerEmail = customerEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }
}
