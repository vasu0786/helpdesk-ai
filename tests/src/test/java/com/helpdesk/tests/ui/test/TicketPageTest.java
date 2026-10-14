package com.helpdesk.tests.ui.test;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.LoginPage;
import com.helpdesk.tests.ui.pages.NewTicketPage;
import com.helpdesk.tests.ui.pages.TicketPage;

public class TicketPageTest extends BaseUiTest {

    @Test(description = "Customer sees no staff controls")
    public void customerSeesNoStaffControls()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        loginPage.waitForUrl("my-tickets.html");

        // Raising a ticket opens its page, so the customer is now on their own ticket
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket("UI test ticket " + System.currentTimeMillis(), "Raised by the UI automation test");
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");

        Assert.assertTrue(ticketPage.isReplyBoxDisplayed());               // a customer can reply
        Assert.assertFalse(ticketPage.isActionsPanelDisplayed());          // but cannot change status, priority or assignee
        Assert.assertFalse(ticketPage.isInternalNoteOptionDisplayed());    // cannot write internal notes
        Assert.assertFalse(ticketPage.isDeleteButtonDisplayed());          // cannot delete
    }

    // The agent raises a ticket for the customer. The browser is then on the page of that ticket.
    private TicketPage agentRaisesTicket()
    {
        loginAs("agent");
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicketForCustomer(ConfigReader.get("customer.email"),
                "UI test ticket " + System.currentTimeMillis(), "Raised by the UI automation test");
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        return ticketPage;
    }

    // Logs the current user out and logs in as another role
    private void switchUser(TicketPage ticketPage, String role)
    {
        ticketPage.logoutSession();
        ticketPage.waitForUrl("login.html");
        loginAs(role);
    }

    @Test(description = "Customer reply is shown")
    public void customerReplyIsShown()
    {
        loginAs("customer");
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket("UI test ticket " + System.currentTimeMillis(), "Raised by the UI automation test");
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");

        String reply = "Customer reply " + System.currentTimeMillis();
        ticketPage.reply(reply);

        Assert.assertEquals(ticketPage.getCommentBodies(), List.of(reply));
    }

    @Test(description = "Customer never sees internal notes")
    public void customerNeverSeesInternalNotes()
    {
        TicketPage ticketPage = agentRaisesTicket();
        String id = ticketPage.getTicketId();
        ticketPage.addInternalNote("Internal note for staff only");
        ticketPage.reply("Public reply for the customer");
        Assert.assertEquals(ticketPage.getCommentBodies(),
                List.of("Internal note for staff only", "Public reply for the customer"));   // the agent sees both

        switchUser(ticketPage, "customer");
        ticketPage.openTicket(id);

        Assert.assertEquals(ticketPage.getCommentBodies(), List.of("Public reply for the customer"));
    }

    @Test(description = "Status dropdown offers only allowed moves")
    public void statusDropdownOffersOnlyAllowedMoves()
    {
        TicketPage ticketPage = agentRaisesTicket();
        Assert.assertEquals(ticketPage.getStatusOptions(), List.of("Open", "In progress", "Closed"));

        ticketPage.selectStatus("In progress");
        ticketPage.saveChanges();

        Assert.assertEquals(ticketPage.getStatus(), "In progress");
        Assert.assertEquals(ticketPage.getStatusOptions(), List.of("In progress", "Waiting on customer", "Resolved"));
    }

    @Test(description = "Agent changes priority and assignee")
    public void agentChangesPriorityAndAssignee()
    {
        TicketPage ticketPage = agentRaisesTicket();
        Assert.assertEquals(ticketPage.getPriority(), "Medium");
        Assert.assertEquals(ticketPage.getAssignee(), "Unassigned");

        ticketPage.selectPriority("High");
        ticketPage.selectAssignee(ConfigReader.get("agent2.name") + " (agent)");
        ticketPage.saveChanges();

        Assert.assertEquals(ticketPage.getPriority(), "High");
        Assert.assertEquals(ticketPage.getAssignee(), ConfigReader.get("agent2.name"));
    }

    @Test(description = "Closed ticket cannot be replied to")
    public void closedTicketCannotBeRepliedTo()
    {
        TicketPage ticketPage = agentRaisesTicket();
        Assert.assertTrue(ticketPage.isReplyBoxDisplayed());

        ticketPage.selectStatus("Closed");
        ticketPage.saveChanges();

        Assert.assertEquals(ticketPage.getStatus(), "Closed");
        Assert.assertFalse(ticketPage.isReplyBoxDisplayed());            // the reply box is gone
        Assert.assertTrue(ticketPage.isClosedNoteDisplayed());           // "This ticket is closed." is shown instead
    }

    @Test(description = "Only an admin can delete a ticket")
    public void onlyAdminCanDeleteTicket()
    {
        TicketPage ticketPage = agentRaisesTicket();
        String id = ticketPage.getTicketId();
        Assert.assertTrue(ticketPage.isActionsPanelDisplayed());
        Assert.assertFalse(ticketPage.isDeleteButtonDisplayed());        // an agent has no Delete button

        switchUser(ticketPage, "admin");
        ticketPage.openTicket(id);
        Assert.assertTrue(ticketPage.isDeleteButtonDisplayed());

        ticketPage.deleteTicket();
        ticketPage.waitForUrl("dashboard.html");                         // after deleting, the admin is sent to the dashboard

        ticketPage.openTicket(id);
        Assert.assertEquals(ticketPage.getErrorMessage(), "Ticket " + id + " not found");
    }
}
