package com.helpdesk.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class MyTicketsPage extends BasePage {

    private final By ticketLink = byTestId("ticket-link");

    public MyTicketsPage(WebDriver driver) {
        super(driver);
    }

    // Every row has the same test id, so this reads the first row, which is the newest ticket
    public String getFirstTicketSubject() {
        return getText(ticketLink);
    }
}
