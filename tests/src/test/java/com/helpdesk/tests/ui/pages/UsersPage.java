package com.helpdesk.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class UsersPage extends BasePage {

    private final By name = byTestId("user-name");
    private final By email = byTestId("user-email");
    private final By password = byTestId("user-password");
    private final By role = byTestId("user-role");
    private final By submit = byTestId("user-submit");
    private final By createSuccess = byTestId("user-create-success");

    public UsersPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        open("/users.html");
    }

    /** roleText is the text in the dropdown: "Agent", "Admin" or "Customer" */
    public void createUser(String fullName, String userEmail, String userPassword, String roleText) {
        type(name, fullName);
        type(email, userEmail);
        type(password, userPassword);
        selectByText(role, roleText);
        click(submit);
    }

    public String getCreateSuccessMessage() {
        return getText(createSuccess);
    }

    // The row of one user is found by the email shown in it
    private String rowOf(String userEmail) {
        return "//tr[@data-testid='user-row'][td[text()='" + userEmail + "']]";
    }

    public String getRole(String userEmail) {
        return getText(By.xpath(rowOf(userEmail) + "//*[@data-testid='user-role-badge']"));
    }

    /** "Active" or "Deactivated" */
    public String getStatus(String userEmail) {
        return getText(By.xpath(rowOf(userEmail) + "//*[@data-testid='user-active']"));
    }

    /** Clicks the Deactivate / Activate button of the user and waits until the status shows expectedStatus */
    public void toggleActive(String userEmail, String expectedStatus) {
        click(By.xpath(rowOf(userEmail) + "//*[@data-testid='user-toggle-active']"));
        wait.until(ExpectedConditions.textToBe(
                By.xpath(rowOf(userEmail) + "//*[@data-testid='user-active']"), expectedStatus));
    }
}
