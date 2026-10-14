package com.helpdesk.tests.ui.base;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.helpdesk.tests.api.utils.ConfigReader;
import com.helpdesk.tests.ui.driver.DriverFactory;
import com.helpdesk.tests.ui.pages.LoginPage;

public class BaseUiTest {

    protected WebDriver driver;

    // A fresh browser for every test, so one test never depends on another
    @BeforeMethod
    public void openBrowser() {
        DriverFactory.startDriver();
        driver = DriverFactory.getDriver();
    }

    /** Logs in through the login page. role is "customer", "agent", "agent2" or "admin" (the keys in config.properties). */
    protected void loginAs(String role) {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open();
        loginPage.login(ConfigReader.get(role + ".email"), ConfigReader.get(role + ".password"));
        loginPage.waitForUrl(role.equals("customer") ? "my-tickets.html" : "dashboard.html");
    }

    @AfterMethod(alwaysRun = true)
    public void closeBrowser(ITestResult result) throws IOException {
        if (driver != null && result.getStatus() == ITestResult.FAILURE) {
            saveScreenshot(result.getMethod().getMethodName());
        }
        DriverFactory.quitDriver();
    }

    // Failed tests leave a picture in target/screenshots/<testName>.png
    private void saveScreenshot(String testName) throws IOException {
        File picture = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Path target = Path.of("target", "screenshots", testName + ".png");
        Files.createDirectories(target.getParent());
        Files.copy(picture.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
