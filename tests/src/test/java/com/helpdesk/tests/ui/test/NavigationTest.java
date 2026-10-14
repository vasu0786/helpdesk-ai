package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.LoginPage;

public class NavigationTest extends BaseUiTest {

    @Test(description = "Customer sees only customer links")
    public void customerSeesOnlyCustomerLinks()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("customer.email"), ConfigReader.get("customer.password"));
        loginPage.waitForUrl("my-tickets.html");

        Assert.assertTrue(loginPage.isNavLinkDisplayed("my-tickets"));
        Assert.assertTrue(loginPage.isNavLinkDisplayed("new-ticket"));
        Assert.assertFalse(loginPage.isNavLinkDisplayed("dashboard"));
        Assert.assertFalse(loginPage.isNavLinkDisplayed("users"));
    }

    @Test(description = "Agent sees Dashboard but not Users")
    public void agentSeesDashboardButNotUsers()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("agent.email"), ConfigReader.get("agent.password"));
        loginPage.waitForUrl("dashboard.html");

        Assert.assertTrue(loginPage.isNavLinkDisplayed("dashboard"));
        Assert.assertTrue(loginPage.isNavLinkDisplayed("new-ticket"));
        Assert.assertFalse(loginPage.isNavLinkDisplayed("users"));
        Assert.assertFalse(loginPage.isNavLinkDisplayed("my-tickets"));
    }

    @Test(description = "Admin sees  Users")
    public void adminSeesUsers()
    {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get("admin.email"), ConfigReader.get("admin.password"));
        loginPage.waitForUrl("dashboard.html");

        Assert.assertTrue(loginPage.isNavLinkDisplayed("dashboard"));
        Assert.assertTrue(loginPage.isNavLinkDisplayed("new-ticket"));
        Assert.assertTrue(loginPage.isNavLinkDisplayed("users"));
        Assert.assertFalse(loginPage.isNavLinkDisplayed("my-tickets"));
    }
}
