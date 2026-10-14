package com.helpdesk.tests.ui.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class DashboardPage extends BasePage {

    private final By pageInfo = byTestId("page-info");                 // e.g. "380 tickets · page 1 of 19"
    private final By ticketSubjects = byTestId("ticket-link");
    private final By ticketStatuses = By.cssSelector("[data-testid='ticket-row'] [data-testid='ticket-status']");
    private final By searchBox = byTestId("filter-search");
    private final By statusFilter = byTestId("filter-status");
    private final By clearButton = byTestId("filter-clear");
    private final By previousButton = byTestId("page-prev");
    private final By nextButton = byTestId("page-next");

    public DashboardPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        open("/dashboard.html");
    }

    /** name is "open", "inProgress", "waitingOnCustomer", "slaBreached" or "unassigned" */
    public int getStat(String name) {
        return Integer.parseInt(getText(By.cssSelector("[data-testid='stat-" + name + "'] .n")));
    }

    public String getPageInfo() {
        return getText(pageInfo);
    }

    /** Subjects of the tickets on the current page, top row first */
    public List<String> getTicketSubjects() {
        getPageInfo();      // the table has loaded
        return getTexts(ticketSubjects);
    }

    /** Status of every ticket on the current page */
    public List<String> getTicketStatuses() {
        getPageInfo();
        return getTexts(ticketStatuses);
    }

    /** Clicks the subject of the top row, which opens that ticket */
    public void openFirstTicket() {
        click(ticketSubjects);
    }

    public void filterByStatus(String statusText) {
        String before = getPageInfo();
        // Typing the option's text picks it the way a keyboard user would. Selenium's Select class is not used
        // here because it fires only the "change" event, and this filter form reloads the table on "input".
        waitForVisible(statusFilter).sendKeys(statusText);
        waitForTableToChange(before);
    }

    public void search(String text) {
        String before = getPageInfo();
        type(searchBox, text);
        waitForTableToChange(before);
    }

    public void clearFilters() {
        String before = getPageInfo();
        click(clearButton);
        waitForTableToChange(before);
    }

    public void goToNextPage() {
        String before = getPageInfo();
        click(nextButton);
        waitForTableToChange(before);
    }

    public void goToPreviousPage() {
        String before = getPageInfo();
        click(previousButton);
        waitForTableToChange(before);
    }

    public boolean isPreviousEnabled() {
        return waitForVisible(previousButton).isEnabled();
    }

    // The table reloads a moment after a filter or page change. The line under the table changes when it has.
    private void waitForTableToChange(String pageInfoBefore) {
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBe(pageInfo, pageInfoBefore)));
    }
}
