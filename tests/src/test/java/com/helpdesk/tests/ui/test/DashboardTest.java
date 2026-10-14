package com.helpdesk.tests.ui.test;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.DashboardPage;
import com.helpdesk.tests.ui.pages.NewTicketPage;
import com.helpdesk.tests.ui.pages.TicketPage;

public class DashboardTest extends BaseUiTest {

    // The agent raises a ticket with a subject that no other ticket has, and returns that subject
    private String raiseTicket()
    {
        String subject = "UI test ticket " + System.currentTimeMillis();
        NewTicketPage newTicketPage = new NewTicketPage(driver);
        newTicketPage.open();
        newTicketPage.raiseTicketForCustomer(ConfigReader.get("customer.email"), subject, "Raised by the UI automation test");
        new TicketPage(driver).waitForUrl("ticket.html?id=");
        return subject;
    }

    @Test(description = "Dashboard shows counts and newest tickets first")
    public void dashboardShowsCountsAndNewestTicketsFirst()
    {
        loginAs("agent");
        DashboardPage dashboard = new DashboardPage(driver);
        int openBefore = dashboard.getStat("open");

        String subject = raiseTicket();
        dashboard.clickNavLink("dashboard");

        Assert.assertEquals(dashboard.getStat("open"), openBefore + 1);         // the count went up by one
        Assert.assertEquals(dashboard.getTicketSubjects().get(0), subject);     // the new ticket is the top row
    }

    @Test(description = "Filter by status and clear")
    public void filterByStatusAndClear()
    {
        loginAs("agent");
        DashboardPage dashboard = new DashboardPage(driver);
        String allTickets = dashboard.getPageInfo();

        dashboard.filterByStatus("Closed");
        List<String> statuses = dashboard.getTicketStatuses();
        Assert.assertFalse(statuses.isEmpty());
        for (String status : statuses) {
            Assert.assertEquals(status, "Closed");
        }

        dashboard.clearFilters();
        Assert.assertEquals(dashboard.getPageInfo(), allTickets);               // the full list is back
    }

    @Test(description = "Search by subject")
    public void searchBySubject()
    {
        loginAs("agent");
        String subject = raiseTicket();

        DashboardPage dashboard = new DashboardPage(driver);
        dashboard.clickNavLink("dashboard");
        dashboard.search(subject);

        Assert.assertEquals(dashboard.getTicketSubjects(), List.of(subject));   // only that one ticket is listed
    }

    @Test(description = "Next and Previous page")
    public void nextAndPreviousPage()
    {
        loginAs("agent");
        DashboardPage dashboard = new DashboardPage(driver);
        List<String> firstPage = dashboard.getTicketSubjects();
        Assert.assertTrue(dashboard.getPageInfo().contains("page 1 of"));
        Assert.assertFalse(dashboard.isPreviousEnabled());                      // nothing before page 1

        dashboard.goToNextPage();
        Assert.assertTrue(dashboard.getPageInfo().contains("page 2 of"));
        Assert.assertTrue(dashboard.isPreviousEnabled());
        Assert.assertNotEquals(dashboard.getTicketSubjects(), firstPage);       // different tickets are shown

        dashboard.goToPreviousPage();
        Assert.assertTrue(dashboard.getPageInfo().contains("page 1 of"));
        Assert.assertEquals(dashboard.getTicketSubjects(), firstPage);
    }
}
