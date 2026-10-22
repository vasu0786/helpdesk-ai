package com.helpdesk.tests.listeners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FailureReportListener implements ITestListener {
    private static final List<Map<String, Object>> failures = new ArrayList<>();


    @Override
    public void onTestFailure(ITestResult result) {
        Throwable error = result.getThrowable();
        Map<String, Object> failure = new LinkedHashMap<>();
        

        failure.put("fingerprint", result.getTestClass().getRealClass().getSimpleName()
        + "." + result.getMethod().getMethodName());
        failure.put("className", result.getTestClass().getName());
        failure.put("methodName", result.getMethod().getMethodName());
        failure.put("errorType", error.getClass().getName());
        failure.put("message", error.getMessage());
        failure.put("parameters", Arrays.deepToString(result.getParameters()));

        List<String> stackTrace = new ArrayList<>();
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("com.helpdesk")) {
                stackTrace.add(frame.toString());
            }
        }
        failure.put("stackTrace", stackTrace);

        failures.add(failure);


      
    }

    @Override
    public void onFinish(ITestContext context) {
        try {
            new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(new File("target/test-failures.json"), failures);
        } catch (IOException e) {
            System.out.println("Could not write test-failures.json: " + e.getMessage());
        }
    }
}
