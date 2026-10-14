package com.helpdesk.tests.ui.test;
import com.helpdesk.tests.ui.pages.LoginPage;
import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import org.testng.annotations.Test;
import org.testng.Assert;
import com.helpdesk.tests.ui.pages.DashboardPage;

public class LoginPageTest extends BaseUiTest {

    @Test(description = "Customer logs in and lands on My Tickets")
    public void customerLoginOnTicketPage()
    {
         LoginPage loginPage = new LoginPage(driver);
         loginPage.open();
         loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        String actualText = loginPage.getHeaderText();
        Assert.assertTrue(actualText.contains(ConfigReader.get("customer.name")));
    }

    @Test(description = "Agent logs in and lands on the Dashboard")
    public void agentLogsIN()
    {
         LoginPage loginPage = new LoginPage(driver);
         loginPage.open();
         loginPage.login(ConfigReader.get("agent.email"), ConfigReader.get("agent.password"));
         loginPage.waitForUrl("dashboard.html");
         Assert.assertTrue(loginPage.getCurrentUrl().contains("dashboard.html"));
         Assert.assertTrue(driver.getTitle().startsWith("Dashboard") );

      
    }
     @Test(description = "login with incorrect creds")
    public void loginWithWrongCreds()
    {
         LoginPage loginPage = new LoginPage(driver);
         loginPage.open();
         loginPage.login(ConfigReader.get("customer.email"), "test123");
         System.out.println("thisis the error message "+loginPage.getErrorMessage());
         Assert.assertTrue(loginPage.getErrorMessage().startsWith("Invalid email or password"));
    }
    @Test(description = "Protected page redirects to login and back")
    public void protectPageRedirectsToLogin()
    {
        DashboardPage dashboard = new DashboardPage(driver);
        dashboard.open();
        dashboard.waitForUrl("login.html");
        Assert.assertTrue(dashboard.getCurrentUrl().contains("next="));
        LoginPage loginPage = new LoginPage(driver);
         loginPage.login(ConfigReader.get("agent.email"), ConfigReader.get("agent.password"));
         Assert.assertTrue(loginPage.getCurrentUrl().contains("dashboard"));
    }
    @Test(description = "logging out ends the session")
    public void loggingOutSessionOut()
    {
         LoginPage loginPage = new LoginPage(driver);
         loginPage.open();
         loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
         loginPage.waitForUrl("my-tickets.html");
         loginPage.logoutSession();
         driver.navigate().back();
         Assert.assertTrue(loginPage.getCurrentUrl().contains("next="));
}
}



