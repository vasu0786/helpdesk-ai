package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.DashboardPage;

public class SessionTest extends BaseUiTest {

    @Test(description = "Invalid or expired token sends the user to login")
    public void invalidTokenSendsUserToLogin()
    {
        loginAs("agent");
        DashboardPage dashboard = new DashboardPage(driver);
        Assert.assertTrue(dashboard.getPageInfo().contains("tickets"));      // the dashboard works with the real token

        // Replace the token with one the server will reject, then load the page again
        dashboard.replaceStoredToken("this.is.not.a.valid.token");
        dashboard.refreshPage();

        dashboard.waitForUrl("login.html");
        Assert.assertTrue(dashboard.getCurrentUrl().contains("login.html"));
    }
}
