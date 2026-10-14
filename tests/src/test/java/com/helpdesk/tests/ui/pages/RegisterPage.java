package com.helpdesk.tests.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


public class RegisterPage extends BasePage {

    private final By fullName = byTestId("register-name");
    private final By email = byTestId("register-email");
    private final By password = byTestId("register-password");
    private final By createAccount = byTestId("register-submit");
    private final By error = byTestId("register-error");

    public RegisterPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        open("/register.html");
    }

     public void register(String userEmail, String userPassword, String userName) {
        type(fullName,userName);
        type(email, userEmail);
        type(password, userPassword);
        click(createAccount);
    }
    public String errorMsg()
    {
        return getText(error);
    }
}