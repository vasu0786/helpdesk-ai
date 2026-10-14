package com.helpdesk.tests.ui.pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.helpdesk.tests.api.utils.ConfigReader;

/** Common actions for every page. All of them wait for the element first (explicit wait). */
public class BasePage {

    protected WebDriver driver;
    protected WebDriverWait wait;
    private final By logout = byTestId("logout-button");

    public BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver,
                Duration.ofSeconds(Long.parseLong(ConfigReader.get("ui.timeout.seconds"))));
    }

    /** Every element in the app has a data-testid, e.g. byTestId("login-email") */
    protected By byTestId(String testId) {
        return By.cssSelector("[data-testid='" + testId + "']");
    }

    /** path is the page address, e.g. open("/login.html") */
    protected void open(String path) {
        driver.get(ConfigReader.get("base.url") + path);
    }

    protected WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected void click(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    protected void type(By locator, String text) {
        WebElement field = waitForVisible(locator);
        field.clear();
        field.sendKeys(text);
    }

    protected String getText(By locator) {
        return waitForVisible(locator).getText();
    }

    protected void selectByText(By locator, String visibleText) {
        new Select(waitForVisible(locator)).selectByVisibleText(visibleText);
    }

    /** The text of every element that matches, e.g. all rows of a table. Tries again if the page redraws the list meanwhile. */
    protected List<String> getTexts(By locator) {
        return wait.ignoring(StaleElementReferenceException.class).until(d -> {
            List<String> texts = new ArrayList<>();
            for (WebElement element : d.findElements(locator)) {
                texts.add(element.getText());
            }
            return texts;
        });
    }

    /** Does not wait and does not fail: use it to check that something is NOT on the page */
    protected boolean isDisplayed(By locator) {
        return !driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed();
    }

    /** Waits until the address contains the text, e.g. waitForUrl("dashboard.html") */
    public void waitForUrl(String partOfUrl) {
        wait.until(ExpectedConditions.urlContains(partOfUrl));
    }

    /** true if a browser pop-up box (JavaScript alert) is open right now */
    public boolean isAlertPresent() {
        try {
            driver.switchTo().alert();
            return true;
        } catch (NoAlertPresentException e) {
            return false;
        }
    }

    /** Overwrites the login token that the app keeps in the browser's local storage */
    public void replaceStoredToken(String newValue) {
        ((JavascriptExecutor) driver).executeScript("localStorage.setItem('helpdesk.token', arguments[0]);", newValue);
    }

    public void refreshPage() {
        driver.navigate().refresh();
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    /** linkName is the last part of the link's test id: "dashboard", "my-tickets", "new-ticket" or "users" */
    public boolean isNavLinkDisplayed(String linkName) {
        waitForVisible(logout);      // the navigation bar has been drawn
        return isDisplayed(byTestId("nav-" + linkName));
    }

    public void logoutSession()
    {
        wait.until(ExpectedConditions.visibilityOfElementLocated(logout)).click();
    }
    /** linkName is the last part of the link's test id: "dashboard", "my-tickets", "new-ticket" or "users" */
    public void clickNavLink(String linkName) {
        click(byTestId("nav-" + linkName));
    }

}
