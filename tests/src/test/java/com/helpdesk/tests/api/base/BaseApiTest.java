package com.helpdesk.tests.api.base;
import com.helpdesk.tests.api.utils.ConfigReader;
import org.testng.annotations.BeforeClass;

import io.restassured.RestAssured;

public class BaseApiTest {

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = ConfigReader.get("base.url");
        RestAssured.basePath = ConfigReader.get("base.path");

    }
}