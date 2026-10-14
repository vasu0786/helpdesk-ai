package com.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "helpdesk")
public record HelpdeskProperties(Jwt jwt, Sla sla, Seed seed) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Sla(Duration urgent, Duration high, Duration medium, Duration low, Duration checkInterval) {
    }

    public record Seed(boolean enabled) {
    }
}
