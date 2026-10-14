package com.helpdesk.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class LoginPage extends BasePage {

    private final By email = byTestId("login-email");
    private final By password = byTestId("login-password");
    private final By submit = byTestId("login-submit");
    private final By error = byTestId("login-error");
    private final By navUser = byTestId("nav-user");
    public LoginPage(WebDriver driver) {
        super(driver);
    }
    public String getHeaderText()
    {
        return getText(navUser);
    }

    public void open() {
        open("/login.html");
    }

    public void login(String userEmail, String userPassword) {
        type(email, userEmail);
        type(password, userPassword);
        click(submit);
    }

    public String getErrorMessage() {
        return getText(error);
    }
}
