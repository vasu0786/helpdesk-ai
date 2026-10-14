package com.helpdesk.tests.ui.test;
import com.helpdesk.tests.ui.pages.RegisterPage;
import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import org.testng.annotations.Test;
import org.testng.Assert;
import com.helpdesk.tests.ui.pages.LoginPage;
public class RegisterPageTest extends BaseUiTest{
    @Test(description = "New customer registers")
    public void newCustomerRegisters()
    {
        RegisterPage registerPage = new RegisterPage(driver);
        registerPage.open();
        String userName= "New User";
        registerPage.register("newuser"+System.currentTimeMillis()+"@test.com", "Test@1234", userName);
        registerPage.waitForUrl("my-tickets.html");
        LoginPage loginPage = new LoginPage(driver);
        Assert.assertTrue(registerPage.getCurrentUrl().contains("my-tickets.html"));
        Assert.assertTrue(loginPage.getHeaderText().contains(userName));
    }
    @Test(description = "existing email Shows an error")
    public void registerExistingEmail()
    {
        RegisterPage registerPage = new RegisterPage(driver);
        registerPage.open();
        String userName= "New User";
        registerPage.register(ConfigReader.get("customer.email"), "Test@1234", userName);
        Assert.assertTrue(registerPage.errorMsg().contains("A user with email customer@helpdesk.local already exists"));
    }
}
