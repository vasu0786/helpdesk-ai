package com.helpdesk.tests.ui.driver;

import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.helpdesk.tests.api.utils.ConfigReader;

public class DriverFactory {

    // One browser per thread, so tests can run in parallel without sharing a browser
    private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();

    public static WebDriver getDriver() {
        return driver.get();
    }

    /** Opens the browser named in config.properties. Selenium Manager downloads the driver itself. */
    public static void startDriver() {
        String browser = ConfigReader.get("browser").toLowerCase();
        boolean headless = Boolean.parseBoolean(ConfigReader.get("headless"));

        WebDriver newDriver;
        switch (browser) {
            case "chrome":
                ChromeOptions chrome = new ChromeOptions();
                if (headless) chrome.addArguments("--headless=new");
                chrome.addArguments("--window-size=1440,900");
                // Switch off Chrome's password pop-ups ("Save password?", "Password found in a data breach").
                // They appear after a login and take the keyboard away from the page.
                chrome.setExperimentalOption("prefs", Map.of(
                        "credentials_enable_service", false,
                        "profile.password_manager_enabled", false,
                        "profile.password_manager_leak_detection", false));
                newDriver = new ChromeDriver(chrome);
                break;
            case "firefox":
                FirefoxOptions firefox = new FirefoxOptions();
                if (headless) firefox.addArguments("-headless");
                firefox.addArguments("--width=1440", "--height=900");
                newDriver = new FirefoxDriver(firefox);
                break;
            case "edge":
                EdgeOptions edge = new EdgeOptions();
                if (headless) edge.addArguments("--headless=new");
                edge.addArguments("--window-size=1440,900");
                newDriver = new EdgeDriver(edge);
                break;
            default:
                throw new RuntimeException("Unknown browser in config.properties: " + browser);
        }
        driver.set(newDriver);
    }

    public static void quitDriver() {
        if (driver.get() != null) {
            driver.get().quit();
            driver.remove();
        }
    }
}
