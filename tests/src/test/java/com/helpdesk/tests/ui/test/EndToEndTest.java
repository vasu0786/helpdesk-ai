package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.DashboardPage;
import com.helpdesk.tests.ui.pages.NewTicketPage;
import com.helpdesk.tests.ui.pages.TicketPage;

public class EndToEndTest extends BaseUiTest {

    @Test(description = "Full journey across two roles")
    public void fullJourneyAcrossTwoRoles()
    {
        // 1. The customer raises a ticket
        loginAs("customer");
        String subject = "UI test ticket " + System.currentTimeMillis();
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket(subject, "My invoice shows the wrong amount");
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        String id = ticketPage.getTicketId();
        ticketPage.logoutSession();
        ticketPage.waitForUrl("login.html");

        // 2. The agent finds it on the dashboard, picks it up, replies and resolves it
        loginAs("agent");
        DashboardPage dashboard = new DashboardPage(driver);
        dashboard.search(subject);
        dashboard.openFirstTicket();
        ticketPage.waitForUrl("ticket.html?id=" + id);

        ticketPage.selectStatus("In progress");
        ticketPage.saveChanges();
        String reply = "We have corrected the invoice";
        ticketPage.reply(reply);
        ticketPage.selectStatus("Resolved");
        ticketPage.saveChanges();
        Assert.assertEquals(ticketPage.getStatus(), "Resolved");
        ticketPage.logoutSession();
        ticketPage.waitForUrl("login.html");

        // 3. The customer opens the ticket again and sees the result
        loginAs("customer");
        ticketPage.openTicket(id);
        Assert.assertEquals(ticketPage.getSubject(), subject);
        Assert.assertEquals(ticketPage.getStatus(), "Resolved");
        Assert.assertTrue(ticketPage.getCommentBodies().contains(reply));
        Assert.assertEquals(ticketPage.getAssignee(), ConfigReader.get("agent.name"));   // the agent who picked it up
    }
}
