package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.LoginPage;
import com.helpdesk.tests.ui.pages.MyTicketsPage;
import com.helpdesk.tests.ui.pages.NewTicketPage;
import com.helpdesk.tests.ui.pages.TicketPage;

public class NewTicketTest extends BaseUiTest {

    @Test(description = "Customer raises a ticket")
    public void customerRaisesTicket()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        loginPage.waitForUrl("my-tickets.html");

        String subject = "UI test ticket " + System.currentTimeMillis();
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket(subject, "Raised by the UI automation test");

        // After submitting, the app opens the page of the new ticket
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        Assert.assertEquals(ticketPage.getCreatedMessage(), "Ticket submitted. Our team will get back to you soon.");
        Assert.assertEquals(ticketPage.getSubject(), subject);
    }
    @Test(description = "Subject is Mandatory")
    public void subjectIsMandatory()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        loginPage.waitForUrl("my-tickets.html");

        
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicketWithoutSubject( "Raised by the UI automation test");

        // After submitting, the app opens the page of the new ticket
        Assert.assertEquals(newTicketPage.getErrorMsg(), "Validation failed");
    }
    @Test(description = "Agent raises a ticket for a customer")
    public void agentRaisesTicketForCustomer()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("agent.email"), ConfigReader.get("agent.password"));
        loginPage.waitForUrl("dashboard.html");

        String subject = "UI test ticket " + System.currentTimeMillis();
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicketForCustomer(ConfigReader.get("customer.email"), subject, "Raised by the agent in a UI test");

        // The ticket page shows the customer the ticket was raised for, not the agent
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        Assert.assertEquals(ticketPage.getSubject(), subject);
        Assert.assertEquals(ticketPage.getCustomer(), ConfigReader.get("customer.email"));
    }

    @Test(description = "Customer sees new ticket")
    public void customerSeesNewTicket()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        loginPage.waitForUrl("my-tickets.html");

        String subject = "UI test ticket " + System.currentTimeMillis();
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket(subject, "Raised by the UI automation test");
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        ticketPage.clickMyTicket();
        ticketPage.waitForUrl("my-tickets");

        // The list is sorted newest first, so the ticket just raised is the top row
        MyTicketsPage myTicketsPage = new MyTicketsPage(driver);
        Assert.assertEquals(myTicketsPage.getFirstTicketSubject(), subject);
    }
}
