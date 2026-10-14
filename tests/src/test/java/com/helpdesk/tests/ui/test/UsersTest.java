package com.helpdesk.tests.ui.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.helpdesk.tests.ui.base.BaseUiTest;
import com.helpdesk.tests.ui.pages.UsersPage;

public class UsersTest extends BaseUiTest {

    @Test(description = "Admin creates a user")
    public void adminCreatesUser()
    {
        loginAs("admin");
        String email = "uiagent" + System.currentTimeMillis() + "@test.com";
        UsersPage usersPage = new UsersPage(driver);
        usersPage.open();
        usersPage.createUser("UI Test Agent", email, "Agent@123", "Agent");

        Assert.assertEquals(usersPage.getCreateSuccessMessage(), "Created " + email + " (agent).");
        Assert.assertEquals(usersPage.getRole(email), "Agent");                 // the new user is in the table
        Assert.assertEquals(usersPage.getStatus(email), "Active");
    }

    @Test(description = "Admin deactivates and re-activates a user")
    public void adminDeactivatesAndReactivatesUser()
    {
        loginAs("admin");
        String email = "uiagent" + System.currentTimeMillis() + "@test.com";
        UsersPage usersPage = new UsersPage(driver);
        usersPage.open();
        usersPage.createUser("UI Test Agent", email, "Agent@123", "Agent");
        Assert.assertEquals(usersPage.getStatus(email), "Active");

        usersPage.toggleActive(email, "Deactivated");
        Assert.assertEquals(usersPage.getStatus(email), "Deactivated");

        usersPage.toggleActive(email, "Active");
        Assert.assertEquals(usersPage.getStatus(email), "Active");
    }
}
