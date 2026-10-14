package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.MyTicketsPage;
import com.helpdesk.tests.ui.pages.NewTicketPage;
import com.helpdesk.tests.ui.pages.TicketPage;

public class SecurityTest extends BaseUiTest {

    @Test(description = "Script text in a subject is shown as plain text")
    public void scriptInSubjectIsShownAsPlainText()
    {
        loginAs("customer");
        // If the page ran this text as code, a pop-up box would open
        String subject = "<script>alert('xss " + System.currentTimeMillis() + "')</script>";
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicket(subject, "<img src=x onerror=alert('xss')>");

        // On the ticket page the text is shown exactly as typed, and no pop-up box opens
        TicketPage ticketPage = new TicketPage(driver);
        ticketPage.waitForUrl("ticket.html?id=");
        Assert.assertEquals(ticketPage.getSubject(), subject);
        Assert.assertFalse(ticketPage.isAlertPresent());

        // The same in the list of tickets
        ticketPage.clickNavLink("my-tickets");
        MyTicketsPage myTicketsPage = new MyTicketsPage(driver);
        myTicketsPage.waitForUrl("my-tickets.html");
        Assert.assertEquals(myTicketsPage.getFirstTicketSubject(), subject);
        Assert.assertFalse(myTicketsPage.isAlertPresent());
    }
}
