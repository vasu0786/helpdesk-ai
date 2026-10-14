package com.helpdesk.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class NewTicketPage extends BasePage {

    private final By customerEmail = byTestId("ticket-customer-email");   // shown to agents and admins only
    private final By subject = byTestId("ticket-subject");
    private final By description = byTestId("ticket-description");
    private final By submit = byTestId("ticket-submit");
    private final By error = byTestId("ticket-form-error");
    public NewTicketPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        open("/new-ticket.html");
    }

    public void raiseTicket(String ticketSubject, String ticketDescription) {
        type(subject, ticketSubject);
        type(description, ticketDescription);
        click(submit);
    }

    /** For an agent or admin, who must say which customer the ticket is for */
    public void raiseTicketForCustomer(String email, String ticketSubject, String ticketDescription) {
        type(customerEmail, email);
        raiseTicket(ticketSubject, ticketDescription);
    }

    public String getErrorMsg()
    {
        return getText(error);
    }

    public void raiseTicketWithoutSubject(String ticketDescription)
    {
        type(description, ticketDescription);
        click(submit);
    }
}
